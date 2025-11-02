/**
 * Concurrency-safe state machine that backs the process pool. Classes in this package coordinate worker inventory,
 * lease scheduling, and shutdown transitions while hiding the underlying lock discipline from higher layers. The
 * public API interacts with these types indirectly through {@link com.github.ulviar.icli.engine.pool.api.ProcessPool}.
 *
 * <p>Key components:
 * <ul>
 *     <li>{@code PoolState} — serialises every change under a fair lock and exposes immutable outcomes that the pool
 *         can act on outside the critical section.</li>
 *     <li>{@code CapacityLedger} — tracks worker counts, queues, and diagnostic counters.</li>
 *     <li>{@code WorkerRetirementPolicy} — evaluates reuse, lifetime, and idle thresholds derived from
 *         configuration.</li>
 *     <li>{@code RetiredWorker}, {@code PoolStateCounters}, and related records — immutable snapshots that make the
 *         state observable to diagnostics and tests without widening the production API.</li>
 * </ul>
 *
 * <p>These classes are internal by design; documentation captures invariants relied upon by neighbouring packages
 * such as {@code internal.lease} (leases, timeouts) and {@code internal.worker} (per-worker metadata).
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.internal.state;

import org.jetbrains.annotations.NotNullByDefault;
