package com.github.ulviar.icli.engine.pool.internal.concurrent;

import com.github.ulviar.icli.engine.pool.internal.concurrent.util.Awaiter;
import com.github.ulviar.icli.engine.pool.internal.concurrent.util.Deadline;
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.Nullable;

/**
 * FIFO queue driving worker hand-offs between acquisition threads while all callers hold the shared
 * {@link ReentrantLock}.
 * <p>
 * Each waiter receives a dedicated {@link Condition} so assignments wake exactly one waiter and avoid thundering herds.
 * The queue never relinquishes its lock; instead, callers decide when to park or resume threads, enabling deterministic
 * ordering in the surrounding pool state machine.
 */
public final class WaiterQueue {

    private final ReentrantLock lock;
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();

    /**
     * Creates a queue backed by the provided {@link ReentrantLock}. The lock must be shared with the rest of the pool
     * coordination so waiter operations observe consistent state transitions.
     *
     * @param lock the reentrant lock coordinating pool state
     */
    public WaiterQueue(ReentrantLock lock) {
        this.lock = lock;
    }

    /**
     * Registers a new waiter at the tail of the queue and returns it to the caller. The lock must be held.
     *
     * @return the newly enqueued waiter
     */
    public Waiter enqueue() {
        requireLocked();
        Waiter waiter = new Waiter(lock.newCondition());
        waiters.addLast(waiter);
        return waiter;
    }

    /**
     * Assigns the provided worker to the head waiter, if any. The caller remains responsible for waking the waiter by
     * calling {@link Waiter#awaitAssignment(long)}.
     *
     * @return {@code true} if a waiter received the worker, {@code false} when the queue is empty
     */
    public boolean assignToNext(PoolWorker worker) {
        requireLocked();
        Waiter waiter = waiters.pollFirst();
        if (waiter == null) {
            return false;
        }
        waiter.assign(worker);
        return true;
    }

    /**
     * Cancels the supplied waiter if it is still queued and unassigned, removing it from the queue.
     */
    public void cancel(Waiter waiter) {
        requireLocked();
        if (waiter.cancel()) {
            waiters.remove(waiter);
        }
    }

    /**
     * Cancels and clears every waiter currently queued. Used when the pool transitions to closing.
     */
    public void cancelAll() {
        requireLocked();
        for (Waiter waiter : waiters) {
            waiter.cancel();
        }
        waiters.clear();
    }

    /**
     * Removes the specified waiter without cancelling it. Used by {@link Waiter#awaitAssignment(long)} when timeouts or
     * cancellations occur while still holding the lock.
     */
    public void remove(Waiter waiter) {
        requireLocked();
        waiters.remove(waiter);
    }

    /**
     * Returns {@code true} when no waiters are queued.
     */
    public boolean isEmpty() {
        requireLocked();
        return waiters.isEmpty();
    }

    /**
     * Returns the number of waiters currently queued.
     */
    public int size() {
        requireLocked();
        return waiters.size();
    }

    private void requireLocked() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("WaiterQueue operations require the owning lock");
        }
    }

    /**
     * Result bundle returned by {@link Waiter#awaitAssignment(long)}.
     */
    public sealed interface AwaitOutcome permits AwaitOutcome.Assigned, AwaitOutcome.TimedOut, AwaitOutcome.Cancelled {

        static AwaitOutcome assigned(PoolWorker worker) {
            return new Assigned(worker);
        }

        static AwaitOutcome timedOut() {
            return new TimedOut();
        }

        static AwaitOutcome cancelled() {
            return new Cancelled();
        }

        /**
         * Outcome signalling that a worker was assigned.
         */
        record Assigned(PoolWorker worker) implements AwaitOutcome {

            public Assigned {
                Objects.requireNonNull(worker, "worker");
            }
        }

        /**
         * Outcome signalling that the deadline elapsed before assignment.
         */
        record TimedOut() implements AwaitOutcome {}

        /**
         * Outcome signalling that the waiter was cancelled before receiving a worker.
         */
        record Cancelled() implements AwaitOutcome {}
    }

    public final class Waiter {

        private final Condition condition;

        @Nullable
        private PoolWorker assignedWorker;

        private boolean cancelled;

        Waiter(Condition condition) {
            this.condition = condition;
        }

        /**
         * Returns {@code true} when a worker has already been assigned to this waiter.
         */
        public boolean isAssigned() {
            return assignedWorker != null;
        }

        /**
         * Blocks until a worker is assigned, the caller cancels the waiter, or the deadline elapses.
         *
         * @param deadlineNanos absolute {@link System#nanoTime()} deadline; {@code 0} means wait indefinitely
         * @return an {@link AwaitOutcome} communicating success, timeout, or cancellation
         * @throws InterruptedException when the thread is interrupted while waiting on the condition
         */
        public AwaitOutcome awaitAssignment(long deadlineNanos) throws InterruptedException {
            requireLocked();
            Deadline deadline = Deadline.fromAbsoluteNanos(deadlineNanos);
            Awaiter.Result result = Awaiter.await(lock, condition, deadline, () -> !isAssigned() && !cancelled);
            if (result == Awaiter.Result.TIMED_OUT && assignedWorker == null && !cancelled) {
                waiters.remove(this);
                return AwaitOutcome.timedOut();
            }
            if (isAssigned()) {
                PoolWorker worker = Objects.requireNonNull(assignedWorker, "assignedWorker");
                return AwaitOutcome.assigned(worker);
            }
            waiters.remove(this);
            return AwaitOutcome.cancelled();
        }

        /**
         * Assigns a worker to this waiter and wakes the thread blocked in {@link #awaitAssignment(long)}.
         */
        private void assign(PoolWorker worker) {
            assignedWorker = worker;
            condition.signal();
        }

        /**
         * Cancels the waiter when it has not yet been assigned, waking any waiting thread. Returns {@code true} if the
         * waiter transitioned to cancelled.
         */
        private boolean cancel() {
            if (isAssigned() || cancelled) {
                return false;
            }
            cancelled = true;
            condition.signal();
            return true;
        }

        /**
         * Detaches the assigned worker, if any, so callers can recover it when the waiting thread aborts before it
         * observes the assignment (for example due to interruption). Callers must hold the shared lock.
         */
        @Nullable
        public PoolWorker stealAssignedIfAny() {
            WaiterQueue.this.requireLocked();
            PoolWorker worker = assignedWorker;
            if (worker != null) {
                assignedWorker = null;
                cancelled = true;
            }
            return worker;
        }
    }
}
