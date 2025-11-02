package com.github.ulviar.icli.engine.pool.api;

import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;

/**
 * Categorises why a pooled worker is retired instead of returning to the idle queue. Reasons are surfaced through
 * {@link PoolDiagnosticsListener#workerRetired(int, WorkerRetirementReason)} and appear in metrics to support
 * observability.
 */
public enum WorkerRetirementReason {
    /**
     * The pool is closing and no longer admits returning workers.
     */
    POOL_CLOSING,

    /**
     * The pool terminated while the worker was still active.
     */
    POOL_TERMINATED,

    /**
     * Client code called {@link WorkerLease#reset(ResetRequest)})} with a request that explicitly retires the worker.
     */
    RETIRE_REQUESTED,

    /**
     * A reset hook requested retirement by returning {@link
     * com.github.ulviar.icli.engine.pool.api.hooks.ResetOutcome#RETIRE}.
     */
    RESET_HOOK_REQUESTED,

    /**
     * A reset hook threw an exception; the worker is retired to avoid propagating inconsistent state.
     */
    RESET_HOOK_FAILURE,

    /**
     * A request exceeded {@link ProcessPoolConfig#requestTimeout()} and triggered timeout handling.
     */
    REQUEST_TIMEOUT,

    /**
     * The worker remained idle longer than {@link ProcessPoolConfig#maxIdleTime()}.
     */
    IDLE_TIMEOUT,

    /**
     * The worker reached the reuse limit set by {@link ProcessPoolConfig#maxRequestsPerWorker()}.
     */
    REUSE_LIMIT_REACHED,

    /**
     * The worker exceeded the lifetime limit set by {@link ProcessPoolConfig#maxWorkerLifetime()}.
     */
    LIFETIME_EXCEEDED,

    /**
     * The pool is draining and disposes the worker as part of shutdown.
     */
    DRAIN,

    /**
     * Sentinel reason used when a retirement notification is emitted even though the worker remains available.
     * Primarily surfaced for consistency with metrics that never record the event as a retirement.
     */
    NOT_RETIRED
}
