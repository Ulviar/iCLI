package com.github.ulviar.icli.engine.runtime.internal.shutdown;

import com.github.ulviar.icli.engine.ShutdownPlan;
import com.github.ulviar.icli.engine.ShutdownSignal;
import com.github.ulviar.icli.engine.runtime.ProcessShutdownException;
import java.util.concurrent.TimeUnit;

/**
 * Supervises a running process using the configured {@link ShutdownPlan} and delegates termination to a
 * {@link ProcessTerminator}.
 *
 * <p>If the supervising thread is interrupted during shutdown, the executor escalates to a force kill and throws
 * {@link com.github.ulviar.icli.engine.runtime.ProcessShutdownException} so callers can react accordingly.</p>
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
            throw new ProcessShutdownException("Interrupted while waiting for process completion", ex);
        }
    }
}
