package com.github.ulviar.icli.fixture;

/**
 * Utility for computing per-chunk delays across streaming styles.
 */
final class StreamingDelays {
    private StreamingDelays() {}

    static long chunkDelay(
            StreamingStyle style, StreamingProfile profile, RuntimeBounds runtimeBounds, long chunkIndex) {
        return switch (style) {
            case SMOOTH -> Math.max(10L, runtimeBounds.minMillis());
            case BURST ->
                (chunkIndex % profile.burstSize() == 0)
                        ? profile.burstIntervalMillis()
                        : 5L;
            case CHUNKED -> 0L;
        };
    }
}
