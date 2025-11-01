package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.api.ProcessPoolConfig;
import com.github.ulviar.icli.core.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Encapsulates pool-wide retirement heuristics derived from {@link ProcessPoolConfig}. The policy evaluates reuse
 * counts, total lifetime, and idle duration so {@link PoolState} can decide whether to recycle a worker or request its
 * retirement without duplicating threshold logic. Duration-based heuristics treat zero or negative values as disabled
 * so callers can explicitly opt out by configuring non-positive thresholds.
 *
 * <p>The policy is stateless aside from the captured configuration and therefore safe to share between pools.
 */
public final class WorkerRetirementPolicy {

    private final ProcessPoolConfig config;

    public WorkerRetirementPolicy(ProcessPoolConfig config) {
        this.config = config;
    }

    /**
     * Evaluates reuse-count and lifetime limits for the supplied worker, returning the reason for retirement when a
     * threshold has been reached. Callers must pass the current pool clock instant.
     *
     * @param worker worker being evaluated
     * @param now current time according to the pool clock
     * @return retirement reason or {@link Optional#empty()} when the worker should be kept
     */
    Optional<WorkerRetirementReason> shouldRetire(PoolWorker worker, Instant now) {
        if (config.maxRequestsPerWorker() > 0 && worker.reuseCount() >= config.maxRequestsPerWorker()) {
            return Optional.of(WorkerRetirementReason.REUSE_LIMIT_REACHED);
        }
        Duration lifetimeLimit = config.maxWorkerLifetime();
        if (isPositive(lifetimeLimit)) {
            Duration lifetime = Duration.between(worker.createdAt(), now);
            if (!lifetime.isNegative() && lifetime.compareTo(lifetimeLimit) >= 0) {
                return Optional.of(WorkerRetirementReason.LIFETIME_EXCEEDED);
            }
        }
        return Optional.empty();
    }

    /**
     * Evaluates the configured idle timeout for the supplied worker and reports whether it should be retired because it
     * remained unused for longer than {@link ProcessPoolConfig#maxIdleTime()}.
     *
     * @param worker worker being evaluated
     * @param now current time according to the pool clock
     * @return retirement reason or {@link Optional#empty()} when the worker can remain idle
     */
    Optional<WorkerRetirementReason> shouldRetireForIdle(PoolWorker worker, Instant now) {
        Duration idleLimit = config.maxIdleTime();
        if (!isPositive(idleLimit)) {
            return Optional.empty();
        }
        Duration idleDuration = Duration.between(worker.lastUsed(), now);
        if (!idleDuration.isNegative() && idleDuration.compareTo(idleLimit) >= 0) {
            return Optional.of(WorkerRetirementReason.IDLE_TIMEOUT);
        }
        return Optional.empty();
    }

    private static boolean isPositive(Duration duration) {
        return duration.compareTo(Duration.ZERO) > 0;
    }
}
