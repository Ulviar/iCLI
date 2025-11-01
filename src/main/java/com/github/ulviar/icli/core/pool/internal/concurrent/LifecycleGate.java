package com.github.ulviar.icli.core.pool.internal.concurrent;

import com.github.ulviar.icli.core.pool.internal.concurrent.util.Awaiter;
import com.github.ulviar.icli.core.pool.internal.concurrent.util.Deadline;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Coordinates the high-level lifecycle of the process pool while all callers hold the shared {@link ReentrantLock}.
 * <p>
 * The gate exposes explicit transitions to the {@code closing} and {@code terminated} phases and offers a deterministic
 * {@link #awaitDrain(long, BooleanSupplier)} helper that cooperatively waits for active workers to finish. The gate is
 * guarded by the same {@link ReentrantLock} that protects {@code PoolState}, ensuring lifecycle decisions are visible
 * to every thread that participates in pool coordination.
 * <p>
 * <strong>Lock discipline:</strong> every public method (including nested types) requires the lock to be held by the
 * caller. Violations throw {@link IllegalStateException} so misuse is detected immediately during testing.
 */
@SuppressFBWarnings(
        value = {"AT_STALE_THREAD_WRITE_OF_PRIMITIVE"},
        justification = "Lifecycle fields are only accessed while holding the shared lock")
public final class LifecycleGate {

    private final ReentrantLock lock;
    private final Condition stateChanged;

    private boolean closing;
    private boolean terminated;

    /**
     * Creates a new gate bound to the provided {@link ReentrantLock}. The lock is not stored defensively because the
     * surrounding state machine already shares it across all collaborators.
     *
     * @param lock the reentrant lock guarding pool state
     */
    public LifecycleGate(ReentrantLock lock) {
        this.lock = lock;
        this.stateChanged = lock.newCondition();
    }

    /**
     * Returns whether the pool has entered the closing phase. Callers must hold the lock.
     */
    public boolean isClosing() {
        requireLocked();
        return closing;
    }

    /**
     * Returns whether the pool reached the terminal state. Callers must hold the lock.
     */
    public boolean isTerminated() {
        requireLocked();
        return terminated;
    }

    /**
     * Marks the pool as closing, waking all waiters so they can observe the transition and fail fast. The method
     * returns {@code true} only on the first transition and is idempotent on subsequent calls.
     *
     * @return {@code true} if the state changed, {@code false} otherwise
     */
    public boolean markClosing() {
        requireLocked();
        if (closing) {
            return false;
        }
        closing = true;
        stateChanged.signalAll();
        return true;
    }

    /**
     * Marks the pool as terminated and notifies all waiters. Invocations after the first one are ignored.
     */
    public void markTerminated() {
        requireLocked();
        if (terminated) {
            return;
        }
        terminated = true;
        stateChanged.signalAll();
    }

    /**
     * Waits until {@code hasActiveWorkers} reports {@code false} or the supplied deadline elapses.
     *
     * @param deadlineNanos the absolute deadline expressed as {@link System#nanoTime()}; {@code 0} means wait
     *     indefinitely
     * @param hasActiveWorkers supplier consulted while still holding the lock that reports whether workers are active
     * @return a {@link DrainOutcome} that captures completion, timeout, and whether this call transitioned the gate
     *     to {@code terminated}
     * @throws InterruptedException if the waiting thread is interrupted while blocked on the condition
     */
    public DrainOutcome awaitDrain(long deadlineNanos, BooleanSupplier hasActiveWorkers) throws InterruptedException {
        requireLocked();
        Deadline deadline = Deadline.fromAbsoluteNanos(deadlineNanos);
        Awaiter.Result result = Awaiter.await(lock, stateChanged, deadline, hasActiveWorkers);
        if (result == Awaiter.Result.TIMED_OUT) {
            return DrainOutcome.timedOut();
        }
        boolean terminatedNow = !terminated;
        if (terminatedNow) {
            terminated = true;
        }
        stateChanged.signalAll();
        return DrainOutcome.completed(terminatedNow);
    }

    /**
     * Wakes all threads awaiting lifecycle changes. Callers typically invoke this after altering pool state while still
     * holding the shared lock.
     */
    public void signalStateChange() {
        requireLocked();
        stateChanged.signalAll();
    }

    private void requireLocked() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("LifecycleGate operations require the owning lock");
        }
    }

    /**
     * Outcome describing how {@link LifecycleGate#awaitDrain(long, BooleanSupplier)} finished.
     */
    public sealed interface DrainOutcome permits DrainOutcome.Completed, DrainOutcome.TimedOut {

        static DrainOutcome completed(boolean terminatedNow) {
            return new Completed(terminatedNow);
        }

        static DrainOutcome timedOut() {
            return new TimedOut();
        }

        /**
         * Signals that draining finished; {@link #terminatedNow()} indicates whether this call transitioned the pool
         * into the terminated state.
         */
        record Completed(boolean terminatedNow) implements DrainOutcome {}

        /**
         * Signals that the deadline elapsed before all workers finished.
         */
        record TimedOut() implements DrainOutcome {}
    }
}
