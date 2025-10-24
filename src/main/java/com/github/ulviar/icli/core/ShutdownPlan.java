package com.github.ulviar.icli.core;

import java.time.Duration;

/** Describes how the runtime escalates termination once a timeout or cancellation occurs. */
public record ShutdownPlan(Duration softTimeout, Duration gracePeriod, ShutdownSignal signal) {

    public ShutdownPlan {
        if (softTimeout.isZero() || softTimeout.isNegative()) {
            throw new IllegalArgumentException("softTimeout must be positive");
        }
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must not be negative");
        }
    }
}
