package com.github.ulviar.icli.fixture;

/**
 * Immutable configuration resolved from CLI flags (or control commands).
 */
public record FixtureConfig(
        FixtureMode mode,
        long startupDelayMillis,
        RuntimeBounds runtimeBounds,
        PayloadProfile payloadProfile,
        StreamingProfile streamingProfile,
        FailurePlan failurePlan,
        NoiseProfile noiseProfile,
        long seed,
        LogFormat logFormat,
        boolean echoEnvironment,
        long streamMaxChunks) {

    public FixtureConfig {
        if (startupDelayMillis < 0) {
            throw new IllegalArgumentException("startupDelayMillis must be >= 0");
        }
        if (streamMaxChunks <= 0) {
            throw new IllegalArgumentException("streamMaxChunks must be positive");
        }
    }
}
