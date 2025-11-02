package com.github.ulviar.icli.engine.pool.api.hooks;

import java.time.Duration;
import java.util.UUID;

/**
 * Coordinates request-level deadlines for active worker leases. Implementations schedule callbacks that fire when a
 * request exceeds its allotted execution time so the pool can fail the lease and retire the worker. The scheduler is
 * owned by {@link com.github.ulviar.icli.engine.pool.api.ProcessPool} and remains operational until the pool finishes
 * draining.
 */
public interface RequestTimeoutScheduler extends AutoCloseable {

    /**
     * Schedules {@code onTimeout} to run once the {@code timeout} elapses for the given {@code workerId} and
     * {@code requestId}. Implementations must replace any previously scheduled timeout for the worker to ensure only
     * the most recent lease drives callbacks.
     *
     * @param workerId identifier of the worker whose lease is being supervised
     * @param requestId request identifier associated with the lease
     * @param timeout duration before {@code onTimeout} is invoked
     * @param onTimeout callback to run when the timeout expires
     */
    void schedule(int workerId, UUID requestId, Duration timeout, Runnable onTimeout);

    /**
     * Cancels the outstanding timeout for {@code workerId}. Invoked when a lease completes before its deadline fires.
     *
     * @param workerId identifier whose timeout should be cancelled
     */
    void cancel(int workerId);

    /**
     * Clears the timeout when the completion notification matches {@code requestId}.
     *
     * @param workerId identifier whose timeout should be cleared
     * @param requestId request identifier associated with the completed lease
     * @return {@code true} when the timeout was active and has been cancelled successfully
     */
    boolean complete(int workerId, UUID requestId);

    @Override
    default void close() {
        // no-op
    }
}
