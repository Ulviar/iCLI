package com.github.ulviar.icli.fixture;

import org.jetbrains.annotations.Nullable;

final class LineSessionState {
    private final FixtureConfig base;
    private RuntimeBounds runtimeBounds;
    private PayloadProfile payloadProfile;
    private StreamingProfile streamingProfile;
    private FailurePlan failurePlan;
    private long requestCounter;
    private boolean hanging;

    LineSessionState(FixtureConfig base) {
        this.base = base;
        this.runtimeBounds = base.runtimeBounds();
        this.payloadProfile = base.payloadProfile();
        this.streamingProfile = base.streamingProfile();
        this.failurePlan = base.failurePlan();
        this.requestCounter = 0L;
        this.hanging = false;
    }

    synchronized RequestPlan plan(@Nullable RequestOverrides overrides) {
        long requestId = ++requestCounter;
        RuntimeBounds runtime =
                overrides != null && overrides.runtimeBounds() != null ? overrides.runtimeBounds() : runtimeBounds;
        PayloadProfile payload =
                overrides != null && overrides.payloadProfile() != null ? overrides.payloadProfile() : payloadProfile;
        FailurePlan failure =
                overrides != null && overrides.failurePlan() != null ? overrides.failurePlan() : failurePlan;
        FixtureMode mode = overrides != null && overrides.mode() != null ? overrides.mode() : FixtureMode.LINE;
        Integer chunkCount = overrides != null ? overrides.chunkCount() : null;
        String label = overrides != null ? overrides.label() : null;
        StreamingStyle streamingStyle = overrides != null && overrides.streamingStyle() != null
                ? overrides.streamingStyle()
                : streamingProfile.style();
        return new RequestPlan(requestId, runtime, payload, failure, mode, chunkCount, label, streamingStyle);
    }

    synchronized void apply(SessionDelta delta) {
        if (delta.runtimeBounds() != null) {
            this.runtimeBounds = delta.runtimeBounds();
        }
        if (delta.payloadProfile() != null) {
            this.payloadProfile = delta.payloadProfile();
        }
        if (delta.streamingProfile() != null) {
            this.streamingProfile = delta.streamingProfile();
        }
        if (delta.failurePlan() != null) {
            this.failurePlan = delta.failurePlan();
        }
    }

    synchronized void reset() {
        this.runtimeBounds = base.runtimeBounds();
        this.payloadProfile = base.payloadProfile();
        this.streamingProfile = base.streamingProfile();
        this.failurePlan = base.failurePlan();
        this.hanging = false;
    }

    synchronized boolean isHanging() {
        return hanging;
    }

    synchronized void enterHang() {
        hanging = true;
    }

    synchronized void leaveHang() {
        hanging = false;
    }
}

record RequestPlan(
        long requestId,
        RuntimeBounds runtimeBounds,
        PayloadProfile payloadProfile,
        FailurePlan failurePlan,
        FixtureMode mode,
        @Nullable Integer chunkCount,
        @Nullable String label,
        StreamingStyle streamingStyle) {}
