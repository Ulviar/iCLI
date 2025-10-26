package com.github.ulviar.icli.client;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal scheduler used by client-facing async helpers. Implementations run blocking tasks on background threads and
 * return a {@link CompletableFuture} that can be cancelled to interrupt the underlying work.
 */
public interface ClientScheduler extends AutoCloseable {

    /**
     * Submit a blocking task for asynchronous execution.
     *
     * @param task blocking work to execute
     * @param <T> type produced by the task
     * @return a {@link CompletableFuture} representing the task result; {@link CompletableFuture#cancel(boolean)} must
     *     interrupt the running task when possible
     */
    <T> CompletableFuture<T> submit(Callable<T> task);

    @Override
    void close();
}
