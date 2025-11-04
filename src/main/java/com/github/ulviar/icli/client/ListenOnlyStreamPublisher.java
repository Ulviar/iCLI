package com.github.ulviar.icli.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

/**
 * Single-subscriber {@link Flow.Publisher} that bridges an {@link InputStream} to reactive consumers. Each subscription
 * drains at most one stream and honours backpressure: new chunks are emitted only after increases demand. Chunks are
 * copied into read-only {@link ByteBuffer ByteBuffers}; callers should treat them as transient snapshots of the
 * underlying pipe/PTY.
 */
final class ListenOnlyStreamPublisher implements Flow.Publisher<ByteBuffer>, AutoCloseable {

    private final InputStream source;
    private final int chunkSize;
    private final String description;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<StreamSubscription> subscription = new AtomicReference<>();

    /**
     * Creates a publisher for the provided stream.
     *
     * @param source readable stream that will be drained until EOF
     * @param chunkSize maximum number of bytes copied into each emitted {@link ByteBuffer}; must be positive
     * @param description human-readable label included in diagnostics when demand contracts are violated
     */
    ListenOnlyStreamPublisher(InputStream source, int chunkSize, String description) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.source = source;
        this.chunkSize = chunkSize;
        this.description = description;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        if (closed.get()) {
            subscriber.onError(new IllegalStateException(description + " publisher is already closed"));
            return;
        }
        StreamSubscription newSubscription = new StreamSubscription(subscriber);
        if (!subscription.compareAndSet(null, newSubscription)) {
            subscriber.onError(new IllegalStateException(description + " publisher already has a subscriber"));
            return;
        }
        subscriber.onSubscribe(newSubscription);
    }

    @Override
    public void close() {
        closed.set(true);
        StreamSubscription current = subscription.get();
        if (current != null) {
            current.cancelInternal();
        }
    }

    private final class StreamSubscription implements Flow.Subscription, Runnable {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicBoolean running = new AtomicBoolean();
        private final Object monitor = new Object();
        private volatile boolean cancelled;

        StreamSubscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                subscriber.onError(
                        new IllegalArgumentException("Demand must be positive for " + description + " publisher"));
                return;
            }
            addDemand(n);
            synchronized (monitor) {
                monitor.notifyAll();
            }
            startPump();
        }

        private void addDemand(long n) {
            while (true) {
                long current = requested.get();
                long updated = (current == Long.MAX_VALUE || n == Long.MAX_VALUE)
                        ? Long.MAX_VALUE
                        : Math.min(Long.MAX_VALUE, current + n);
                if (requested.compareAndSet(current, updated)) {
                    return;
                }
            }
        }

        private void startPump() {
            if (running.compareAndSet(false, true)) {
                Thread.startVirtualThread(() -> {
                    try {
                        run();
                    } finally {
                        running.set(false);
                    }
                });
            }
        }

        @Override
        public void cancel() {
            cancelInternal();
        }

        private void cancelInternal() {
            cancelled = true;
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }

        @Override
        public void run() {
            try {
                while (!cancelled && !closed.get()) {
                    if (!acquireDemand()) {
                        continue;
                    }
                    ByteBuffer buffer = readNext();
                    if (buffer == null) {
                        subscriber.onComplete();
                        cancelInternal();
                        break;
                    }
                    subscriber.onNext(buffer);
                }
            } catch (IOException ex) {
                if (!cancelled && !closed.get()) {
                    subscriber.onError(ex);
                }
            } finally {
                running.set(false);
            }
        }

        private boolean acquireDemand() throws IOException {
            while (!cancelled && !closed.get()) {
                long current = requested.get();
                if (current == Long.MAX_VALUE) {
                    return true;
                }
                if (current > 0 && requested.compareAndSet(current, current - 1)) {
                    return true;
                }
                synchronized (monitor) {
                    if (requested.get() == 0 && !cancelled && !closed.get()) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for demand", ex);
                        }
                    }
                }
            }
            return false;
        }

        @Nullable
        private ByteBuffer readNext() throws IOException {
            while (!cancelled && !closed.get()) {
                byte[] buffer = new byte[chunkSize];
                int read = source.read(buffer);
                if (read == -1 || cancelled || closed.get()) {
                    return null;
                }
                if (read > 0) {
                    return ByteBuffer.wrap(buffer, 0, read).asReadOnlyBuffer();
                }
                // read == 0; loop until bytes become available or stream closes
            }
            return null;
        }
    }
}
