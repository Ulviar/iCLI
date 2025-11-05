package com.github.ulviar.icli.fixture;

import org.jetbrains.annotations.Nullable;

record RequestOverrides(
        @Nullable RuntimeBounds runtimeBounds,
        @Nullable PayloadProfile payloadProfile,
        @Nullable FailurePlan failurePlan,
        @Nullable FixtureMode mode,
        @Nullable Integer chunkCount,
        @Nullable String label,
        @Nullable StreamingStyle streamingStyle) {

    RequestOverrides {
        if (runtimeBounds == null
                && payloadProfile == null
                && failurePlan == null
                && mode == null
                && chunkCount == null
                && label == null
                && streamingStyle == null) {
            throw new IllegalArgumentException("No overrides specified");
        }
    }

    RequestOverrides(
            @Nullable RuntimeBounds runtimeBounds,
            @Nullable PayloadProfile payloadProfile,
            @Nullable FailurePlan failurePlan,
            @Nullable FixtureMode mode) {
        this(runtimeBounds, payloadProfile, failurePlan, mode, null, null, null);
    }
}
