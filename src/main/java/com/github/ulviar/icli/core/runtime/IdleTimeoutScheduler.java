package com.github.ulviar.icli.core.runtime;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper that manages idle-timeout scheduling for interactive sessions.
 * <p>
 * Implementations either execute the supplied callback after {@code timeout}
 * (on a dedicated virtual-thread backed scheduler) or act as a no-op when the
 * timeout is disabled. This keeps {@link ProcessInteractiveSession} free from
 * null sentinels and makes the lifecycle of the timer explicit.
 */
interface IdleTimeoutScheduler extends AutoCloseable {

    static IdleTimeoutScheduler create(Duration timeout, Runnable task) {
        if (timeout.isNegative() || timeout.isZero()) {
            return NoOpIdleTimeoutScheduler.INSTANCE;
        }
        return new ScheduledIdleTimeoutScheduler(timeout, task);
    }

    void reschedule();

    void cancel();

    @Override
    void close();

    final class NoOpIdleTimeoutScheduler implements IdleTimeoutScheduler {
        private static final NoOpIdleTimeoutScheduler INSTANCE = new NoOpIdleTimeoutScheduler();

        private NoOpIdleTimeoutScheduler() {}

        @Override
        public void reschedule() {}

        @Override
        public void cancel() {}

        @Override
        public void close() {}
    }

    final class ScheduledIdleTimeoutScheduler implements IdleTimeoutScheduler {
        private static final ScheduledFuture<?> NO_FUTURE = new CompletedScheduledFuture();

        private final ScheduledExecutorService executor;
        private final Duration timeout;
        private final Runnable task;
        private final AtomicReference<ScheduledFuture<?>> future;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        ScheduledIdleTimeoutScheduler(Duration timeout, Runnable task) {
            this.executor = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("icli-idle", 0).factory());
            this.timeout = timeout;
            this.task = task;
            this.future = new AtomicReference<>(NO_FUTURE);
        }

        @Override
        public void reschedule() {
            if (closed.get()) {
                return;
            }
            ScheduledFuture<?> scheduled = executor.schedule(task, timeout.toMillis(), TimeUnit.MILLISECONDS);
            ScheduledFuture<?> previous = future.getAndSet(scheduled);
            if (previous != NO_FUTURE) {
                previous.cancel(false);
            }
        }

        @Override
        public void cancel() {
            ScheduledFuture<?> previous = future.getAndSet(NO_FUTURE);
            if (previous != NO_FUTURE) {
                previous.cancel(false);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                future.set(NO_FUTURE);
                executor.shutdownNow();
            }
        }
    }

    final class CompletedScheduledFuture implements ScheduledFuture<Void> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            if (o instanceof CompletedScheduledFuture) {
                return 0;
            }
            return -1;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CompletedScheduledFuture;
        }

        @Override
        public int hashCode() {
            return CompletedScheduledFuture.class.hashCode();
        }
    }
}
