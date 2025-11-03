package com.github.ulviar.icli.engine.pool.internal.concurrent.util;

import java.time.Duration;

/**
 * Shared deadline helper wrapping absolute {@link System#nanoTime()} instants with the repository-wide convention that
 * {@code 0} represents an infinite wait.
 *
 * <p>The utility centralises duration conversion, overflow handling, and remaining-time calculations so pool
 * collaborators rely on a single implementation when coordinating blocking operations.
 */
public final class Deadline {

    public static final long INFINITE_NANOS = Long.MAX_VALUE;

    private static final Deadline INFINITE = new Deadline(INFINITE_NANOS);

    private final long absoluteNanos;

    private Deadline(long absoluteNanos) {
        this.absoluteNanos = absoluteNanos;
    }

    /**
     * Constructs a deadline from an absolute {@link System#nanoTime()} reading. Passing {@code 0} produces an infinite
     * deadline.
     */
    public static Deadline fromAbsoluteNanos(long deadlineNanos) {
        if (deadlineNanos == 0) {
            return infinite();
        }
        return new Deadline(deadlineNanos);
    }

    /**
     * Constructs a deadline relative to the current {@link System#nanoTime()} reading. {@link Duration#ZERO} maps to an
     * infinite deadline while negative durations are rejected.
     */
    public static Deadline fromTimeout(Duration timeout) {
        return fromTimeout(timeout, System.nanoTime());
    }

    /**
     * Constructs a deadline relative to {@code now}, expressed as {@link System#nanoTime()} nanoseconds. Exposed so
     * deterministic tests can inject a synthetic clock.
     */
    public static Deadline fromTimeout(Duration timeout, long now) {
        long absolute = toAbsoluteTimeout(timeout, now);
        return fromAbsoluteNanos(absolute);
    }

    /**
     * Returns a deadline that never expires.
     */
    public static Deadline infinite() {
        return INFINITE;
    }

    /**
     * Exposes the absolute {@link System#nanoTime()} deadline represented by this instance. Infinite deadlines return
     * {@link #INFINITE_NANOS}.
     */
    public long absoluteNanos() {
        return absoluteNanos;
    }

    /**
     * Returns {@code true} when the deadline never expires.
     */
    public boolean isInfinite() {
        return absoluteNanos == INFINITE_NANOS;
    }

    /**
     * Computes the nanoseconds remaining before the deadline elapses, clamping to zero when expired. Infinite deadlines
     * report {@link #INFINITE_NANOS}.
     */
    public long remainingNanos() {
        if (isInfinite()) {
            return INFINITE_NANOS;
        }
        long remaining = absoluteNanos - System.nanoTime();
        return remaining <= 0 ? 0 : remaining;
    }

    /**
     * Returns {@code true} when the deadline has already elapsed.
     */
    public boolean isExpired() {
        return !isInfinite() && remainingNanos() == 0;
    }

    /**
     * Converts a {@link Duration} into an absolute {@link System#nanoTime()} deadline using the current time as the
     * reference point. {@link Duration#ZERO} maps to {@code 0} (infinite wait).
     */
    public static long toAbsoluteTimeout(Duration timeout) {
        return toAbsoluteTimeout(timeout, System.nanoTime());
    }

    /**
     * Converts a {@link Duration} into an absolute {@link System#nanoTime()} deadline using {@code now} as the
     * reference point. {@link Duration#ZERO} maps to {@code 0} (infinite wait). Extremely large durations saturate to
     * {@link #INFINITE_NANOS}.
     *
     * @throws IllegalArgumentException when {@code timeout} is negative
     */
    public static long toAbsoluteTimeout(Duration timeout, long now) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        if (timeout.isZero()) {
            return 0;
        }
        long nanos = safeToNanos(timeout);
        if (nanos == INFINITE_NANOS) {
            return INFINITE_NANOS;
        }
        return saturatingAdd(now, nanos);
    }

    private static long safeToNanos(Duration duration) {
        try {
            long nanos = duration.toNanos();
            if (nanos < 0) {
                return INFINITE_NANOS;
            }
            return nanos;
        } catch (ArithmeticException ex) {
            return INFINITE_NANOS;
        }
    }

    private static long saturatingAdd(long base, long delta) {
        long result = base + delta;
        if (((base ^ result) & (delta ^ result)) < 0) {
            return INFINITE_NANOS;
        }
        return result;
    }
}
