package com.github.ulviar.icli.client;

/**
 * Indicates that {@link CommandRunner} failed before a {@link CommandResult} could be produced.
 *
 * <p>The exception wraps unexpected runtime failures originating from lower layers (for example process launch issues
 * or {@link com.github.ulviar.icli.engine.runtime.ProcessEngineExecutionException}). The original {@link CommandCall} is
 * retained so callers can inspect the attempted command and {@link CommandCall#options()} for diagnostics.</p>
 *
 * <h2>Recommended handling</h2>
 * <ul>
 *     <li>
 *         abort the current workflow and surface the error to operators; the underlying process may still be running;
 *     </li>
 *     <li>
 *         inspect {@link #call()} and {@link #getCause()} to decide whether a retry with adjusted options is viable;
 *     </li>
 *     <li>
 *         avoid reusing mutable state derived from the failed invocation because output capture may be truncated.
 *     </li>
 * </ul>
 */
public final class CommandRunnerException extends RuntimeException {

    private final CommandCall call;

    CommandRunnerException(CommandCall call, Throwable cause) {
        super("Command execution failed for: " + call.renderCommandLine(), cause);
        this.call = call;
    }

    /**
     * Provides the immutable command invocation associated with the failure.
     *
     * @return command call attempted by the runner
     */
    public CommandCall call() {
        return call;
    }
}
