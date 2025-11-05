package com.github.ulviar.icli.fixture;

/**
 * Inclusive runtime bounds in milliseconds.
 */
public record RuntimeBounds(long minMillis, long maxMillis) {
    public RuntimeBounds {
        if (minMillis < 0 || maxMillis < 0) {
            throw new IllegalArgumentException("Runtime bounds must be >= 0");
        }
        if (minMillis > maxMillis) {
            throw new IllegalArgumentException("Runtime min must be <= max");
        }
    }
}
