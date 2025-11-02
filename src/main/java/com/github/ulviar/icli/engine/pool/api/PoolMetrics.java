package com.github.ulviar.icli.engine.pool.api;

/**
 * Immutable snapshot describing the current utilisation and history of a {@link ProcessPool}. All counters are
 * monotonic except for the instantaneous values ({@code idleWorkers}, {@code activeWorkers}, {@code pendingWaiters}).
 * Snapshots are safe to cache and compare across time to detect load trends.
 *
 * @param totalWorkers total workers tracked by the pool (idle + leased)
 * @param idleWorkers number of workers immediately available for leasing
 * @param activeWorkers workers currently leased to callers
 * @param pendingWaiters threads waiting for a worker
 * @param minSize configured minimum pool size
 * @param maxSize configured maximum pool size
 * @param queueCapacity configured waiter queue capacity ({@link Integer#MAX_VALUE} when unbounded)
 * @param totalLeasesServed cumulative leases handed out since pool creation
 * @param failedLaunchAttempts workers that failed to launch or warm up
 * @param totalReplenishments number of workers created after pool initialisation
 * @param totalRetirements number of workers retired (voluntary or due to failure)
 */
public record PoolMetrics(
        int totalWorkers,
        int idleWorkers,
        int activeWorkers,
        int pendingWaiters,
        int minSize,
        int maxSize,
        int queueCapacity,
        long totalLeasesServed,
        long failedLaunchAttempts,
        long totalReplenishments,
        long totalRetirements) {}
