package com.github.ulviar.icli.core.runtime.shutdown;

import com.github.ulviar.icli.core.ShutdownPlan;
import com.github.ulviar.icli.core.ShutdownSignal;
import java.util.concurrent.TimeUnit;

/**
 * Supervises a running process using the configured {@link ShutdownPlan} and delegates termination to a
 * {@link ProcessTerminator}.
 */
public final class ShutdownExecutor {

    private final ProcessTerminator terminator;

    public ShutdownExecutor(ProcessTerminator terminator) {
        this.terminator = terminator;
    }

    public void awaitCompletion(Process process, ShutdownPlan plan, boolean destroyTree) {
        try {
            if (!process.waitFor(plan.softTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
                terminator.terminate(process, destroyTree, plan.signal() == ShutdownSignal.KILL);
                if (!process.waitFor(plan.gracePeriod().toNanos(), TimeUnit.NANOSECONDS)) {
                    terminator.terminate(process, destroyTree, true);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            terminator.terminate(process, destroyTree, true);
            throw new RuntimeException("Interrupted while waiting for process completion", ex);
        }
    }
}
