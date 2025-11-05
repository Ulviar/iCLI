package com.github.ulviar.icli.fixture;

import org.jetbrains.annotations.Nullable;

record SessionDelta(
        @Nullable RuntimeBounds runtimeBounds,
        @Nullable PayloadProfile payloadProfile,
        @Nullable StreamingProfile streamingProfile,
        @Nullable FailurePlan failurePlan) {}
