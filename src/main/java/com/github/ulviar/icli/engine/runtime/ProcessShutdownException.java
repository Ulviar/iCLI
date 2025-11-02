package com.github.ulviar.icli.engine.runtime;

/**
 * Indicates that the shutdown sequence for a launched process failed unexpectedly.
 *
 * <p>The exception is raised when the supervising thread is interrupted while applying a
 * {@link com.github.ulviar.icli.engine.ShutdownPlan}, even after the runtime escalates to a forceful kill. At this point
 * the library cannot guarantee that the process tree was fully terminated or that captured output represents the full
 * transcript.</p>
 *
 * <h2>Recommended handling</h2>
 * <ul>
 *     <li>assume the underlying process may still be running and invoke an explicit kill
 *     (for example {@link java.lang.Process#destroyForcibly()}) if you hold a reference;</li>
 *     <li>discard any partial results produced by the command because they may be truncated;</li>
 *     <li>surface the failure to higher-level automation so the run can be retried from a clean state and the
 *     interruption investigated.</li>
 * </ul>
 *
 * <p>The original {@link InterruptedException} is provided as the cause for diagnostics.</p>
 */
public final class ProcessShutdownException extends RuntimeException {

    /**
     * Creates a new exception with the supplied message.
     *
     * @param message human-readable explanation of the shutdown failure
     */
    public ProcessShutdownException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the supplied message and root cause.
     *
     * @param message human-readable explanation of the shutdown failure
     * @param cause   original {@link InterruptedException} triggered during shutdown supervision
     */
    public ProcessShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
