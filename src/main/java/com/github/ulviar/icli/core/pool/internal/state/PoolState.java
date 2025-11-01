package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.api.PoolMetrics;
import com.github.ulviar.icli.core.pool.api.ProcessPoolConfig;
import com.github.ulviar.icli.core.pool.api.ServiceUnavailableException;
import com.github.ulviar.icli.core.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.core.pool.internal.concurrent.LifecycleGate;
import com.github.ulviar.icli.core.pool.internal.concurrent.WaiterQueue;
import com.github.ulviar.icli.core.pool.internal.lease.DefaultLeaseScope;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.Nullable;

/**
 * Serialises every pool lifecycle transition under a fair {@link java.util.concurrent.locks.ReentrantLock}. The class
 * owns the waiter queue, lifecycle gate, and capacity ledger so worker launches, lease assignments, queueing, and
 * shutdown all observe identical ordering. Each public method acquires the shared lock, mutates internal state, and
 * returns an immutable description so callers can perform expensive side effects outside the critical section.
 *
 * <p>Key responsibilities:
 * <ul>
 *     <li>Keep {@link CapacityLedger} invariants valid while workers move between launching, active, idle, and retired.
 *     <li>Coordinate waiters via {@link WaiterQueue} so lease requests honour FIFO order and cancellation semantics.
 *     <li>Track the closing → terminated lifecycle through {@link LifecycleGate}, waking blocked threads as
 *         transitions occur.
 * </ul>
 */
public final class PoolState {

    private static final String MESSAGE_TERMINATED = "Process pool has been terminated";
    private static final String MESSAGE_CLOSING = "Process pool is shutting down";
    private static final String MESSAGE_NO_WORKERS = "No workers available";
    private static final String MESSAGE_QUEUE_FULL_TEMPLATE = "Worker queue is full (pending=%d, capacity=%d)";
    private static final String MESSAGE_TIMEOUT = "Timed out waiting for a pooled worker";
    private static final String MESSAGE_INTERRUPTED = "Interrupted while waiting for a worker";

    private final ProcessPoolConfig config;
    private final CapacityLedger ledger;
    private final LifecycleGate lifecycle;
    private final WaiterQueue waiters;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final boolean invariantChecksEnabled;

    /**
     * Creates a new pool state machine bound to the supplied configuration and retirement policy. Launch decisions are
     * surfaced to callers via {@link #reserveNextForMinimum()} and {@link #onLaunchSuccess(PoolWorker)}, while
     * retirement decisions are exposed through {@link ReleaseResult} and {@link DrainStatus}.
     */
    public PoolState(ProcessPoolConfig config, WorkerRetirementPolicy retirementPolicy) {
        this.config = config;
        this.ledger = new CapacityLedger(config, retirementPolicy);
        this.lifecycle = new LifecycleGate(lock);
        this.waiters = new WaiterQueue(lock);
        this.invariantChecksEnabled = config.invariantChecksEnabled();
    }

    /**
     * Attempts to lease a worker before the supplied absolute {@code deadlineNanos}. When an idle worker is available
     * the method grants a lease immediately; otherwise it either queues the caller or fails fast depending on
     * {@code waitAllowed}. Any workers retired while scanning the idle queue are returned to the caller so they can be
     * disposed without holding the lock.
     *
     * @param deadlineNanos absolute {@link System#nanoTime()} deadline ({@code 0} means wait indefinitely)
     * @param waitAllowed   whether the caller is willing to join the waiter queue if no idle worker is immediately
     *                      available
     *
     * @return an {@link AcquireResult} describing whether a lease was granted, a launch was reserved, or the request
     * failed
     */
    public AcquireResult acquire(long deadlineNanos, boolean waitAllowed) {
        List<RetiredWorker> retired = new ArrayList<>();
        WaiterQueue.Waiter waiter = null;
        lock.lock();
        try {
            while (true) {
                AcquireResult lifecycleFailure = failIfClosedOrTerminated(retired);
                if (!lifecycleFailure.equals(AcquireResult.none())) {
                    return done(lifecycleFailure);
                }

                IdleLeaseOutcome idleOutcome = tryLeaseFromIdleOrServeWaiter(retired);
                if (idleOutcome.hasResult()) {
                    return done(idleOutcome.result());
                }
                if (idleOutcome.shouldContinue()) {
                    continue;
                }

                AcquireResult noWaitFailure = rejectIfNoWait(waitAllowed, retired);
                if (!noWaitFailure.equals(AcquireResult.none())) {
                    return done(noWaitFailure);
                }

                AcquireResult launchReservation = tryReserveLaunch(retired);
                if (!launchReservation.equals(AcquireResult.none())) {
                    return done(launchReservation);
                }

                AcquireResult queueRejection = rejectIfQueueFull(retired);
                if (!queueRejection.equals(AcquireResult.none())) {
                    return done(queueRejection);
                }

                waiter = waiters.enqueue();
                WaiterQueue.AwaitOutcome awaitOutcome = waiter.awaitAssignment(deadlineNanos);
                waiter = null;

                switch (awaitOutcome) {
                    case WaiterQueue.AwaitOutcome.Assigned assigned -> {
                        return done(onWaiterAssigned(assigned.worker(), retired));
                    }
                    case WaiterQueue.AwaitOutcome.TimedOut ignored -> {
                        return done(onWaiterTimedOut(retired));
                    }
                    case WaiterQueue.AwaitOutcome.Cancelled ignored -> {}
                }
            }
        } catch (InterruptedException ex) {
            return done(onAwaitInterrupted(waiter, retired, ex));
        } finally {
            lock.unlock();
        }
    }

    private AcquireResult done(AcquireResult result) {
        assertInvariants();
        return result;
    }

    private IdleLeaseOutcome tryLeaseFromIdleOrServeWaiter(List<RetiredWorker> retired) {
        Instant now = config.clock().instant();
        var idle = ledger.pollIdle(retired, now);
        if (idle.isEmpty()) {
            return IdleLeaseOutcome.none();
        }

        PoolWorker worker = idle.orElseThrow();
        if (!waiters.isEmpty()) {
            if (!waiters.assignToNext(worker)) {
                ledger.enqueueReturnedIdle(worker);
            }
            return IdleLeaseOutcome.continueLoop();
        }

        return IdleLeaseOutcome.leased(beginLease(worker, now, retired));
    }

    private AcquireResult failIfClosedOrTerminated(List<RetiredWorker> retired) {
        if (lifecycle.isTerminated()) {
            return AcquireResult.failed(new ServiceUnavailableException(MESSAGE_TERMINATED), immutable(retired));
        }
        if (lifecycle.isClosing()) {
            return AcquireResult.failed(new ServiceUnavailableException(MESSAGE_CLOSING), immutable(retired));
        }
        return AcquireResult.none();
    }

    private AcquireResult rejectIfNoWait(boolean waitAllowed, List<RetiredWorker> retired) {
        if (waitAllowed) {
            return AcquireResult.none();
        }
        return AcquireResult.failed(new ServiceUnavailableException(MESSAGE_NO_WORKERS), immutable(retired));
    }

    private AcquireResult tryReserveLaunch(List<RetiredWorker> retired) {
        int workerId = ledger.reserveLaunchWorkerId();
        if (workerId == -1) {
            return AcquireResult.none();
        }
        return AcquireResult.launchReserved(workerId, immutable(retired));
    }

    private AcquireResult rejectIfQueueFull(List<RetiredWorker> retired) {
        if (config.maxQueueDepth() == Integer.MAX_VALUE) {
            return AcquireResult.none();
        }
        int pending = waiters.size();
        int capacity = config.maxQueueDepth();
        if (pending < capacity) {
            return AcquireResult.none();
        }
        QueueRejectionDetails details = new QueueRejectionDetails(pending, capacity);
        ServiceUnavailableException exception = new ServiceUnavailableException(
                String.format(Locale.ROOT, MESSAGE_QUEUE_FULL_TEMPLATE, pending, capacity));
        return AcquireResult.queueRejected(exception, immutable(retired), details);
    }

    private AcquireResult onWaiterAssigned(PoolWorker worker, List<RetiredWorker> retired) {
        Instant leaseStart = config.clock().instant();
        return beginLease(worker, leaseStart, retired);
    }

    private AcquireResult onWaiterTimedOut(List<RetiredWorker> retired) {
        return AcquireResult.failed(new ServiceUnavailableException(MESSAGE_TIMEOUT), immutable(retired));
    }

    private AcquireResult onAwaitInterrupted(
            @Nullable WaiterQueue.Waiter waiter, List<RetiredWorker> retired, InterruptedException ex) {
        if (waiter != null) {
            PoolWorker reclaimed = waiter.stealAssignedIfAny();
            if (reclaimed != null) {
                if (!waiters.assignToNext(reclaimed)) {
                    ledger.enqueueReturnedIdle(reclaimed);
                }
            } else {
                waiters.cancel(waiter);
            }
        }
        Thread.currentThread().interrupt();
        return AcquireResult.failed(new ServiceUnavailableException(MESSAGE_INTERRUPTED, ex), immutable(retired));
    }

    private AcquireResult beginLease(PoolWorker worker, Instant leaseStart, List<RetiredWorker> retired) {
        DefaultLeaseScope scope = ledger.beginLease(worker, leaseStart);
        return AcquireResult.leased(worker, scope, immutable(retired));
    }

    private record IdleLeaseOutcome(boolean shouldContinue, AcquireResult result) {

        private static final IdleLeaseOutcome NONE = new IdleLeaseOutcome(false, AcquireResult.none());
        private static final IdleLeaseOutcome CONTINUE = new IdleLeaseOutcome(true, AcquireResult.none());

        static IdleLeaseOutcome none() {
            return NONE;
        }

        static IdleLeaseOutcome continueLoop() {
            return CONTINUE;
        }

        static IdleLeaseOutcome leased(AcquireResult result) {
            return new IdleLeaseOutcome(false, result);
        }

        boolean hasResult() {
            return result != AcquireResult.none();
        }
    }

    /**
     * Records a successful worker launch that was previously reserved via {@link #reserveNextForMinimum()} or during
     * acquisition. The returned {@link LaunchResult} indicates whether the worker was assigned to a waiting caller,
     * queued for future requests, or discarded because the pool is shutting down.
     */
    public LaunchResult onLaunchSuccess(PoolWorker worker) {
        lock.lock();
        try {
            if (lifecycle.isTerminated()) {
                ledger.discardLaunchReservation(false);
                LaunchResult result = LaunchResult.discarded(LaunchDiscardReason.POOL_TERMINATED);
                assertInvariants();
                return result;
            }
            if (lifecycle.isClosing()) {
                ledger.discardLaunchReservation(false);
                LaunchResult result = LaunchResult.discarded(LaunchDiscardReason.POOL_CLOSING);
                assertInvariants();
                return result;
            }

            ledger.registerLaunch();
            if (waiters.isEmpty()) {
                ledger.enqueueReturnedIdle(worker);
                LaunchResult result = LaunchResult.queued();
                assertInvariants();
                return result;
            }

            if (waiters.assignToNext(worker)) {
                LaunchResult result = LaunchResult.assigned();
                assertInvariants();
                return result;
            }

            ledger.enqueueReturnedIdle(worker);
            LaunchResult result = LaunchResult.queued();
            assertInvariants();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases a reserved launch slot after an external launch attempt failed.
     *
     * @param countFailure whether diagnostics should increment the failed-launch counter
     */
    public void onLaunchFailure(boolean countFailure) {
        lock.lock();
        try {
            ledger.discardLaunchReservation(countFailure);
            assertInvariants();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Begins releasing a worker that just completed a lease. The returned {@link ReleasePlan} signals whether the
     * caller should ignore the release, keep the worker in circulation, or retire it for a specific reason.
     */
    public ReleasePlan beginRelease(PoolWorker worker, Instant now) {
        lock.lock();
        try {
            CapacityLedger.LeaseReturn leaseReturn = ledger.returnLease(worker, now);
            if (!leaseReturn.processed()) {
                assertInvariants();
                return ReleasePlan.ignore();
            }
            if (leaseReturn.drainSignalNeeded()) {
                lifecycle.signalStateChange();
            }
            if (lifecycle.isClosing()) {
                ReleasePlan plan = ReleasePlan.retire(WorkerRetirementReason.POOL_CLOSING);
                assertInvariants();
                return plan;
            }
            ReleasePlan plan = ReleasePlan.keep();
            assertInvariants();
            return plan;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Completes the release flow started by {@link #beginRelease(PoolWorker, Instant)}. Depending on the supplied plan
     * the worker is returned to idle state, assigned to the next waiter, or retired and reported back to the caller.
     */
    public ReleaseResult completeRelease(PoolWorker worker, Instant now, ReleasePlan plan) {
        lock.lock();
        try {
            if (plan instanceof ReleasePlan.Retire retirePlan) {
                ledger.retireReturnedWorker();
                ReleaseResult result = ReleaseResult.retired(new RetiredWorker(worker, retirePlan.reason()));
                assertInvariants();
                return result;
            }

            if (worker.retireRequested()) {
                ledger.retireReturnedWorker();
                ReleaseResult result = ReleaseResult.retired(new RetiredWorker(worker, worker.retirementCause()));
                assertInvariants();
                return result;
            }

            var thresholdReason = ledger.retirementThresholdReason(worker, now);
            if (thresholdReason.isPresent()) {
                ledger.retireReturnedWorker();
                ReleaseResult result = ReleaseResult.retired(new RetiredWorker(worker, thresholdReason.get()));
                assertInvariants();
                return result;
            }

            if (waiters.isEmpty()) {
                ledger.enqueueReturnedIdle(worker);
                ReleaseResult result = ReleaseResult.returnedToIdle();
                assertInvariants();
                return result;
            }

            if (waiters.assignToNext(worker)) {
                ReleaseResult result = ReleaseResult.assigned();
                assertInvariants();
                return result;
            }

            ledger.enqueueReturnedIdle(worker);
            ReleaseResult result = ReleaseResult.returnedToIdle();
            assertInvariants();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reserves a single launch slot if the pool is currently below the configured minimum size. Returns an empty
     * optional when no reservation is required or the pool is closing/terminated.
     *
     * <p>Calling this method:
     * <ul>
     *     <li>immediately increments the {@code launching} counter and assigns a unique worker identifier that stays
     *         reserved until {@link #onLaunchSuccess(PoolWorker)} or {@link #onLaunchFailure(boolean)} runs;</li>
     *     <li>yields {@link OptionalInt#empty()} once the minimum size is satisfied,
     *     {@link ProcessPoolConfig#maxSize()} would be exceeded, or the lifecycle has begun closing/terminating;</li>
     *     <li>guarantees monotonically increasing identifiers so tests can assert deterministic worker numbering.</li>
     * </ul>
     *
     * <p>Callers must always resolve the reservation by recording success or failure; skipping either callback will
     * leave the pool permanently over-counting the reserved worker.
     */
    public OptionalInt reserveNextForMinimum() {
        lock.lock();
        try {
            if (lifecycle.isClosing() || lifecycle.isTerminated()) {
                assertInvariants();
                return OptionalInt.empty();
            }
            OptionalInt reservation = ledger.reserveNextForMinimum();
            assertInvariants();
            return reservation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Produces a {@link PoolMetrics} snapshot for diagnostics while holding the coordination lock.
     */
    public PoolMetrics snapshot() {
        lock.lock();
        try {
            PoolMetrics metrics = ledger.snapshot(waiters.size());
            assertInvariants();
            return metrics;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transitions the pool into the closing state, cancelling all waiters so they observe the shutdown promptly. The
     * method returns {@code true} if this call initiated the transition.
     */
    public boolean markClosing() {
        lock.lock();
        try {
            if (!lifecycle.markClosing()) {
                return false;
            }
            waiters.cancelAll();
            assertInvariants();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits for active workers to finish before draining idle workers into {@code retiring}. The supplied deadline is
     * expressed using the {@link System#nanoTime()} convention adopted by the pool ({@code 0} = wait indefinitely).
     * <p>
     * The drain operation only waits for <em>active</em> leases to complete; outstanding launch reservations must still
     * be resolved through {@link #onLaunchSuccess(PoolWorker)} or {@link #onLaunchFailure(boolean)}. Callers should
     * therefore ensure no new launches are scheduled after invoking {@link #markClosing()}.
     *
     * @return {@link DrainStatus} describing whether draining completed and whether this call marked the pool as
     * terminated
     */
    public DrainStatus drain(long deadlineNanos, List<PoolWorker> retiring) {
        lock.lock();
        try {
            LifecycleGate.DrainOutcome outcome = lifecycle.awaitDrain(deadlineNanos, ledger::hasActiveWorkers);
            switch (outcome) {
                case LifecycleGate.DrainOutcome.Completed completed -> {
                    ledger.drainIdleWorkers(retiring);
                    DrainStatus status = new DrainStatus(true, completed.terminatedNow());
                    assertInvariants();
                    return status;
                }
                case LifecycleGate.DrainOutcome.TimedOut ignored -> {
                    DrainStatus status = new DrainStatus(false, false);
                    assertInvariants();
                    return status;
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DrainStatus status = new DrainStatus(false, false);
            assertInvariants();
            return status;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Increments the retirement counter exposed through the diagnostics snapshot.
     */
    public void recordRetirement() {
        lock.lock();
        try {
            ledger.recordRetirement();
            assertInvariants();
        } finally {
            lock.unlock();
        }
    }

    private void assertInvariants() {
        if (!invariantChecksEnabled) {
            return;
        }
        int allocated = ledger.allocatedWorkers();
        int active = ledger.activeWorkers();
        int idle = ledger.idleWorkers();
        int launching = ledger.launchingWorkers();
        int maxSize = config.maxSize();
        int waiterCount = waiters.size();
        boolean closing = lifecycle.isClosing();
        boolean terminated = lifecycle.isTerminated();

        checkState(allocated >= 0, "Allocated workers must remain non-negative (allocated=%d)".formatted(allocated));
        checkState(active >= 0, "Active workers must remain non-negative (active=%d)".formatted(active));
        checkState(idle >= 0, "Idle workers must remain non-negative (idle=%d)".formatted(idle));
        checkState(launching >= 0, "Launching workers must remain non-negative (launching=%d)".formatted(launching));
        int totalWorkers = allocated + launching;
        checkState(
                totalWorkers <= maxSize,
                "Total workers must not exceed max size (allocated=%d, launching=%d, max=%d)"
                        .formatted(allocated, launching, maxSize));
        int knownWorkers = active + idle;
        checkState(
                allocated >= knownWorkers,
                "Allocated workers cannot be less than active + idle (allocated=%d, active=%d, idle=%d)"
                        .formatted(allocated, active, idle));
        checkState(
                !closing || waiterCount == 0,
                "Closing pool must not retain waiter queue entries (waiters=%d)".formatted(waiterCount));
        checkState(
                !terminated || closing,
                "Terminated pool must have been closed (terminated=%s, closing=%s)".formatted(terminated, closing));
    }

    private static void checkState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    /**
     * Exposes internal counters for unit tests that must validate invariants even when JVM assertions are disabled.
     */
    PoolStateCounters debugCounters() {
        lock.lock();
        try {
            return new PoolStateCounters(
                    ledger.allocatedWorkers(),
                    ledger.idleWorkers(),
                    ledger.activeWorkers(),
                    ledger.launchingWorkers(),
                    waiters.size(),
                    lifecycle.isClosing(),
                    lifecycle.isTerminated());
        } finally {
            lock.unlock();
        }
    }

    void assertInvariantsGuarded() {
        assertInvariants();
    }
}
