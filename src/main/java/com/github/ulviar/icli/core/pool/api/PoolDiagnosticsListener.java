package com.github.ulviar.icli.core.pool.api;

import java.time.Duration;
import java.util.UUID;

/**
 * Observer interface for tracking {@link ProcessPool} lifecycle events. The pool invokes every callback on the caller
 * thread that triggered the event, so implementations must be lightweight and non-blocking. Metrics-driven listeners
 * should copy data they need immediately rather than retaining mutable references.
 */
public interface PoolDiagnosticsListener {

    /**
     * Returns a listener that ignores every event. Useful as a default value when no diagnostics are required.
     *
     * @return no-op listener
     */
    static PoolDiagnosticsListener noOp() {
        return new PoolDiagnosticsListener() {};
    }

    /**
     * Notifies that a worker was successfully launched and is now part of the pool.
     *
     * @param workerId identifier of the newly created worker
     */
    default void workerCreated(int workerId) {}

    /**
     * Reports that a worker is being permanently retired. The pool closes the underlying session immediately after
     * firing this callback.
     *
     * @param workerId identifier of the retired worker
     * @param reason reason the worker left the pool
     */
    default void workerRetired(int workerId, WorkerRetirementReason reason) {}

    /**
     * Signals that a worker failed to launch or was discarded during warm-up.
     *
     * @param workerId identifier reserved for the worker
     * @param failure exception describing the failure
     */
    default void workerFailed(int workerId, Throwable failure) {}

    /**
     * Emits when a caller acquires a worker. The worker remains in use until {@link #leaseReleased(int)} is invoked.
     *
     * @param workerId identifier of the leased worker
     */
    default void leaseAcquired(int workerId) {}

    /**
     * Emits when a lease is returned to the pool (either for reuse or retirement).
     *
     * @param workerId identifier of the worker being returned
     */
    default void leaseReleased(int workerId) {}

    /**
     * Reports that a lease exceeded its request deadline and triggered timeout handling.
     *
     * @param workerId identifier of the worker whose lease timed out
     * @param requestId unique request identifier associated with the lease
     */
    default void leaseTimedOut(int workerId, UUID requestId) {}

    /**
     * Indicates that an acquisition attempt was rejected because the wait queue was full.
     *
     * @param pendingWaiters number of callers already queued
     * @param queueCapacity configured maximum queue depth
     */
    default void queueRejected(int pendingWaiters, int queueCapacity) {}

    /**
     * Publishes the latest pool metrics snapshot. The supplied {@link PoolMetrics} instance is immutable.
     *
     * @param metrics current pool metrics
     */
    default void metricsUpdated(PoolMetrics metrics) {}

    /**
     * Signals that {@link ProcessPool#close()} transitioned the pool into the closing state. New acquisitions fail
     * after this callback fires.
     */
    default void poolDraining() {}

    /**
     * Signals that {@link ProcessPool#drain(Duration)} transitioned the pool into the terminated state.
     */
    default void poolTerminated() {}
}
