package com.github.ulviar.icli.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Describes how the runtime escalates termination once a timeout or cancellation occurs.
 *
 * @param softTimeout time to wait before sending {@link #signal()}
 * @param gracePeriod grace period after sending the signal before forceful termination
 * @param signal semantic signal to send for graceful shutdown attempts
 */
public record TerminationPolicy(Duration softTimeout, Duration gracePeriod, TerminationSignal signal) {

    public TerminationPolicy {
        Objects.requireNonNull(softTimeout, "softTimeout");
        Objects.requireNonNull(gracePeriod, "gracePeriod");
        Objects.requireNonNull(signal, "signal");
        if (softTimeout.isZero() || softTimeout.isNegative()) {
            throw new IllegalArgumentException("softTimeout must be positive");
        }
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must not be negative");
        }
    }
}
