package com.github.ulviar.icli.engine.pool.internal.concurrent.util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Shared helper that blocks on a {@link Condition} while a predicate remains {@code true}, respecting a shared
 * {@link Deadline}. The utility ensures identical semantics for waiter queue and lifecycle coordination, including the
 * "zero means infinite" deadline convention, tolerance for spurious wakeups, and propagation of
 * {@link InterruptedException}.
 */
public final class Awaiter {

    private Awaiter() {}

    /**
     * Result enumerating whether the await completed successfully or timed out.
     */
    public enum Result {
        COMPLETED,
        TIMED_OUT
    }

    /**
     * Blocks on the supplied {@link Condition} while the predicate remains {@code true}. Callers must hold {@code lock}
     * before invoking this method.
     *
     * @param lock the shared {@link ReentrantLock} guarding the condition
     * @param condition condition to await
     * @param deadline deadline controlling how long the wait may block
     * @param predicate guard evaluated while holding the lock that determines whether waiting should continue
     * @return {@link Result#COMPLETED} when the predicate became {@code false}; {@link Result#TIMED_OUT} when the
     *     deadline elapsed while the predicate still evaluated to {@code true}
     * @throws InterruptedException if the waiting thread is interrupted; callers are responsible for restoring the
     *     interrupt flag
     * @throws NullPointerException when any argument is {@code null}
     * @throws IllegalStateException when the calling thread does not hold {@code lock}
     */
    public static Result await(ReentrantLock lock, Condition condition, Deadline deadline, BooleanSupplier predicate)
            throws InterruptedException {
        requireHeld(lock);

        while (predicate.getAsBoolean()) {
            if (deadline.isInfinite()) {
                condition.await();
                continue;
            }
            long remaining = deadline.remainingNanos();
            if (remaining <= 0) {
                return Result.TIMED_OUT;
            }
            long timeLeft = condition.awaitNanos(remaining);
            if (timeLeft <= 0 && predicate.getAsBoolean()) {
                return Result.TIMED_OUT;
            }
        }
        return Result.COMPLETED;
    }

    private static void requireHeld(ReentrantLock lock) {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Awaiter requires the owning lock to be held");
        }
    }
}
