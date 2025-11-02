package com.github.ulviar.icli.engine.runtime;

/**
 * Signals that the {@link com.github.ulviar.icli.engine.runtime.StandardProcessEngine} failed while supervising a
 * launched process.
 *
 * <p>The exception wraps operational issues that occur after the process has already been started: interruptions while
 * waiting for completion, or failures draining stdout/stderr pumps. Callers should treat these conditions as fatal for
 * the in-flight command because the runtime can no longer guarantee that the process terminated cleanly or that its
 * output was captured in full.</p>
 *
 * <h2>Recommended handling</h2>
 * <ul>
 *     <li>assume the underlying process may still be running and trigger an explicit shutdown (for example by closing
 *     the session or invoking {@link java.lang.Process#destroyForcibly()});</li>
 *     <li>discard any partial output collected for the command because it may be truncated or inconsistent;</li>
 *     <li>surface the exception to higher layers so automated workflows can report the failure and, if applicable,
 *     retry from a known-clean state.</li>
 * </ul>
 *
 * <p>The engine wraps the original cause where available to aid diagnostics.</p>
 */
public final class ProcessEngineExecutionException extends RuntimeException {

    /**
     * Creates an exception with the supplied message.
     *
     * @param message human-readable explanation of the failure
     */
    public ProcessEngineExecutionException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the supplied message and root cause.
     *
     * @param message human-readable explanation of the failure
     * @param cause   original exception triggered during process supervision
     */
    public ProcessEngineExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
