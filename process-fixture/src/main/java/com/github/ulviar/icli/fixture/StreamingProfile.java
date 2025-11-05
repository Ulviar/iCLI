package com.github.ulviar.icli.fixture;

/**
 * Streaming behaviour configuration.
 */
public record StreamingProfile(StreamingStyle style, long burstIntervalMillis, int burstSize) {
    public StreamingProfile {
        if (burstIntervalMillis < 0) {
            throw new IllegalArgumentException("burstIntervalMillis must be >= 0");
        }
        if (burstSize <= 0) {
            throw new IllegalArgumentException("burstSize must be > 0");
        }
    }
}
