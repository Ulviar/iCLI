package com.github.ulviar.icli.samples.scenarios.single.icli;

import com.github.ulviar.icli.engine.ShutdownPlan;
import com.github.ulviar.icli.engine.ShutdownSignal;
import java.time.Duration;

/** Shared helpers for producing shutdown plans consistent across the iCLI samples. */
final class IcliShutdownPlans {

    private static final Duration MAX_GRACE = Duration.ofSeconds(5);

    private IcliShutdownPlans() {}

    static ShutdownPlan forTimeout(Duration timeout) {
        Duration grace = timeout.compareTo(MAX_GRACE) > 0 ? MAX_GRACE : timeout;
        return new ShutdownPlan(timeout, grace, ShutdownSignal.INTERRUPT);
    }
}
