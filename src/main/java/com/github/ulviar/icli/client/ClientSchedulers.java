package com.github.ulviar.icli.client;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jetbrains.annotations.Nullable;

/**
 * Factory helpers for {@link ClientScheduler} implementations.
 */
public final class ClientSchedulers {

    private ClientSchedulers() {}

    /**
     * Create a scheduler backed by a virtual-thread-per-task executor. Callers should close the returned instance when
     * no longer needed to allow the executor to shut down.
     */
    public static ClientScheduler virtualThreads() {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("icli-client", 0).factory());
        return new ExecutorBackedClientScheduler(executor, true);
    }

    /**
     * Wrap a caller-provided executor service.
     *
     * @param executor executor service responsible for running tasks
     * @param shutdownOnClose whether {@link ClientScheduler#close()} should invoke {@link ExecutorService#shutdown()}
     * @return scheduler delegating to the provided executor
     */
    public static ClientScheduler usingExecutorService(ExecutorService executor, boolean shutdownOnClose) {
        return new ExecutorBackedClientScheduler(executor, shutdownOnClose);
    }

    private static final class ExecutorBackedClientScheduler implements ClientScheduler {
        private final ExecutorService executor;
        private final boolean shutdownOnClose;

        ExecutorBackedClientScheduler(ExecutorService executor, boolean shutdownOnClose) {
            this.executor = executor;
            this.shutdownOnClose = shutdownOnClose;
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> task) {
            ScheduledFuture<T> future = new ScheduledFuture<>();
            Future<?> delegate = executor.submit(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
            future.attach(delegate);
            return future;
        }

        @Override
        public void close() {
            if (shutdownOnClose) {
                executor.shutdown();
            }
        }
    }

    private static final class ScheduledFuture<T> extends CompletableFuture<T> {
        @Nullable
        private volatile Future<?> delegate;

        void attach(Future<?> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                Future<?> local = delegate;
                if (local != null) {
                    local.cancel(mayInterruptIfRunning);
                }
            }
            return cancelled;
        }
    }
}
