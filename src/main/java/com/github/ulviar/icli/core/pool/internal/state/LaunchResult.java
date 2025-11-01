package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;

/**
 * Outcome returned by {@link PoolState#onLaunchSuccess(PoolWorker)} describing how the pool should treat a newly
 * launched worker.
 */
public sealed interface LaunchResult permits LaunchResult.Queued, LaunchResult.Assigned, LaunchResult.Discarded {

    static LaunchResult queued() {
        return Queued.INSTANCE;
    }

    static LaunchResult assigned() {
        return Assigned.INSTANCE;
    }

    static LaunchResult discarded(LaunchDiscardReason reason) {
        return new Discarded(reason);
    }

    /**
     * The worker was queued for future acquisitions.
     */
    enum Queued implements LaunchResult {
        INSTANCE
    }

    /**
     * The worker was assigned immediately to a waiting caller.
     */
    enum Assigned implements LaunchResult {
        INSTANCE
    }

    /**
     * The worker was discarded because the pool is shutting down or otherwise unable to use it.
     */
    public record Discarded(LaunchDiscardReason reason) implements LaunchResult {}
}
