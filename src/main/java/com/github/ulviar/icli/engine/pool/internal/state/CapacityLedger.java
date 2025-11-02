package com.github.ulviar.icli.engine.pool.internal.state;

import com.github.ulviar.icli.engine.pool.api.PoolMetrics;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.engine.pool.internal.lease.DefaultLeaseScope;
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/**
 * Worker inventory tracker owned by {@link PoolState}. The ledger is responsible for counting every worker that exists
 * in the pool (allocated, idle, active, launching) and for incrementing aggregate counters surfaced through
 * {@link com.github.ulviar.icli.engine.pool.api.PoolMetrics}. It never acquires locks itself; callers must invoke it
 * while already holding the shared {@link java.util.concurrent.locks.ReentrantLock} guarded by {@code PoolState}.
 *
 * <p>Core invariants enforced by the ledger:
 * <ul>
 *     <li>{@code allocatedWorkers >= activeWorkers + idleWorkers} — no worker can be simultaneously active and idle.
 *     <li>{@code allocatedWorkers + launchingWorkers <= config.maxSize()} — prevents runaway launches.
 *     <li>All counters remain non-negative while the lock is held.
 * </ul>
 *
 * <p>The ledger also collects diagnostic counters such as total leases served, launch failures, and retirements so pool
 * diagnostics can observe long-term behaviour without re-computing derived totals. Every method must be invoked while
 * holding the {@link PoolState} lock; failure to do so would break the invariants documented above.
 */
final class CapacityLedger {

    private final ProcessPoolConfig config;
    private final WorkerRetirementPolicy retirementPolicy;
    private final ArrayDeque<PoolWorker> idleWorkers = new ArrayDeque<>();

    private int allocatedWorkers;
    private int activeWorkers;
    private int launchingWorkers;
    private int nextWorkerId = 1;
    private long totalLeasesServed;
    private long failedLaunches;
    private long totalReplenishments;
    private long totalRetirements;

    CapacityLedger(ProcessPoolConfig config, WorkerRetirementPolicy retirementPolicy) {
        this.config = config;
        this.retirementPolicy = retirementPolicy;
    }

    /**
     * Returns the next idle worker that is safe to lease or {@link Optional#empty()} if none are available. Workers
     * that have been marked for retirement or crossed idle thresholds are removed from inventory and recorded in the
     * supplied {@code retired} list so callers can dispose them after leaving the critical section.
     */
    Optional<PoolWorker> pollIdle(List<RetiredWorker> retired, Instant now) {
        while (!idleWorkers.isEmpty()) {
            PoolWorker candidate = idleWorkers.removeFirst();
            if (candidate.retireRequested()) {
                retireIdleWorker(retired, candidate, candidate.retirementCause());
                continue;
            }
            Optional<WorkerRetirementReason> reason = retirementPolicy.shouldRetireForIdle(candidate, now);
            if (reason.isPresent()) {
                retireIdleWorker(retired, candidate, reason.get());
                continue;
            }
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Transitions a worker into the active state and assigns it a new {@link DefaultLeaseScope}. {@link PoolState}
     * remains responsible for wiring callbacks to the scope; the ledger only updates counters.
     */
    DefaultLeaseScope beginLease(PoolWorker worker, Instant leaseStart) {
        DefaultLeaseScope scope = new DefaultLeaseScope(worker, leaseStart);
        worker.markLeased(scope.requestId());
        activeWorkers++;
        totalLeasesServed++;
        return scope;
    }

    /**
     * Records that a lease finished and returns a {@link LeaseReturn} describing whether the operation updated state
     * and whether the caller should wake threads blocked on
     * {@link com.github.ulviar.icli.engine.pool.internal.concurrent.LifecycleGate#awaitDrain(long, BooleanSupplier)}.
     */
    LeaseReturn returnLease(PoolWorker worker, Instant now) {
        if (!worker.markReturned(now)) {
            return LeaseReturn.ignored();
        }
        requireState(activeWorkers > 0, "active workers cannot underflow when lease returns");
        activeWorkers--;
        boolean drainSignalNeeded = activeWorkers == 0;
        return LeaseReturn.processed(drainSignalNeeded);
    }

    /**
     * Evaluates pool-wide retirement thresholds (reuse count, lifetime) for the supplied worker and reports whether it
     * should be retired.
     */
    Optional<WorkerRetirementReason> retirementThresholdReason(PoolWorker worker, Instant now) {
        return retirementPolicy.shouldRetire(worker, now);
    }

    /**
     * Removes a worker that was already returned from an active lease and is being permanently retired.
     */
    void retireReturnedWorker() {
        requireState(allocatedWorkers > 0, "allocated workers cannot underflow when retiring returned worker");
        allocatedWorkers--;
    }

    /**
     * Places a worker back into the idle queue so future acquisitions can reuse it.
     */
    void enqueueReturnedIdle(PoolWorker worker) {
        requireState(!worker.retireRequested(), "cannot enqueue worker scheduled for retirement");
        idleWorkers.addLast(worker);
    }

    /**
     * Reserves a worker identifier for launch if capacity allows. Callers must invoke
     * {@link #discardLaunchReservation(boolean)} or {@link #registerLaunch()} to release the reservation.
     *
     * @return a positive identifier when the reservation succeeds or {@code -1} when the pool is already at capacity
     */
    int reserveLaunchWorkerId() {
        if (allocatedWorkers + launchingWorkers >= config.maxSize()) {
            return -1;
        }
        launchingWorkers++;
        return nextWorkerId++;
    }

    /**
     * Releases a launch reservation, optionally counting it as a failed launch for diagnostics.
     */
    void discardLaunchReservation(boolean countFailure) {
        requireState(launchingWorkers > 0, "launching workers cannot underflow when discarding reservation");
        launchingWorkers--;
        if (countFailure) {
            failedLaunches++;
        }
    }

    /**
     * Confirms that a worker finished launching and moves it into the allocated set.
     */
    void registerLaunch() {
        requireState(launchingWorkers > 0, "launching workers cannot underflow when registering launch");
        launchingWorkers--;
        allocatedWorkers++;
        totalReplenishments++;
    }

    OptionalInt reserveNextForMinimum() {
        if (config.minSize() <= 0) {
            return OptionalInt.empty();
        }
        if (allocatedWorkers + launchingWorkers >= config.minSize()
                || allocatedWorkers + launchingWorkers >= config.maxSize()) {
            return OptionalInt.empty();
        }
        launchingWorkers++;
        return OptionalInt.of(nextWorkerId++);
    }

    /**
     * Moves every idle worker into the supplied {@code sink} collection and decrements the allocated count. Callers use
     * this during drain so they can shut down workers after leaving the critical section.
     */
    void drainIdleWorkers(List<PoolWorker> sink) {
        while (!idleWorkers.isEmpty()) {
            PoolWorker worker = idleWorkers.removeFirst();
            sink.add(worker);
            requireState(allocatedWorkers > 0, "allocated workers cannot underflow when draining idle");
            allocatedWorkers--;
        }
    }

    boolean hasActiveWorkers() {
        return activeWorkers > 0;
    }

    /**
     * Returns the current {@link PoolMetrics} snapshot including pool-level counters and queue depth.
     */
    PoolMetrics snapshot(int pendingWaiters) {
        return new PoolMetrics(
                allocatedWorkers,
                idleWorkers.size(),
                activeWorkers,
                pendingWaiters,
                config.minSize(),
                config.maxSize(),
                config.maxQueueDepth(),
                totalLeasesServed,
                failedLaunches,
                totalReplenishments,
                totalRetirements);
    }

    /**
     * Increments the retirement counter used by {@link PoolMetrics}.
     */
    void recordRetirement() {
        totalRetirements++;
    }

    int allocatedWorkers() {
        return allocatedWorkers;
    }

    int activeWorkers() {
        return activeWorkers;
    }

    int idleWorkers() {
        return idleWorkers.size();
    }

    int launchingWorkers() {
        return launchingWorkers;
    }

    /**
     * Immutable summary returned by {@link #returnLease(PoolWorker, Instant)} describing whether state changed and
     * whether threads blocked on drain should be nudged.
     *
     * @param processed {@code true} when the lease transitioned from active to idle
     * @param drainSignalNeeded {@code true} when no active workers remain and drain waiters should be signalled
     */
    record LeaseReturn(boolean processed, boolean drainSignalNeeded) {

        static LeaseReturn ignored() {
            return new LeaseReturn(false, false);
        }

        static LeaseReturn processed(boolean drainSignalNeeded) {
            return new LeaseReturn(true, drainSignalNeeded);
        }
    }

    private void retireIdleWorker(List<RetiredWorker> retired, PoolWorker worker, WorkerRetirementReason reason) {
        requireState(allocatedWorkers > 0, "allocated workers cannot underflow when retiring idle");
        allocatedWorkers--;
        retired.add(new RetiredWorker(worker, reason));
    }

    private static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
