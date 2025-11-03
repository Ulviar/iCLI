package com.github.ulviar.icli.engine;

/**
 * Pluggable gateway encapsulating the core execution engine.
 *
 * <p>Consumers treat this interface as the single source of truth for process execution semantics: implementations must
 * honour the contracts described below regardless of their internal structure.
 *
 * <h2>General rules</h2>
 *
 * <ul>
 *   <li>Arguments must be non-null and derived from the API layer; implementations must not mutate them.</li>
 *   <li>{@link CommandDefinition} specifies the launch command, working directory, environment overrides, terminal
 *       preference, and optional shell wrapping. Unsupported terminal requirements (e.g., {@code REQUIRED} when PTY
 *       support is unavailable) must raise {@link UnsupportedOperationException} rather than silently downgrading.</li>
 *   <li>{@link ExecutionOptions} dictates stdout/stderr capture policies, merge behaviour, shutdown sequencing, and
 *       process-tree handling. Implementations must treat these values as authoritative.</li>
 * </ul>
 */
public interface ProcessEngine {

    /**
     * Execute a single command to completion using pipes (or PTY when supported/requested) and return a summary result.
     *
     * <p><strong>Preconditions:</strong>
     *
     * <ul>
     *   <li>{@code spec.command()} must not be empty (enforced by {@link CommandDefinition}).</li>
     *   <li>If {@code spec.terminalPreference() == REQUIRED}, the implementation must be able to provision a
     *   PTY/ConPTY; otherwise it must throw {@link UnsupportedOperationException}.</li>
     * </ul>
     *
     * <p><strong>Postconditions:</strong>
     *
     * <ul>
     *   <li>
     *       The external process has exited (successfully or not) and any resources (pipes, PTY handles) are released.
     *   </li>
     *   <li>
     *       {@link ProcessResult#exitCode()} reflects the process exit status. The caller is responsible for turning
     *       non-zero exits into domain-specific errors.
     *   </li>
     *   <li>
     *       {@link ProcessResult#stdout()} and {@link ProcessResult#stderr()} contain decoded output according to the
     *       configured {@link OutputCapture} policies. When {@link ExecutionOptions#mergeErrorIntoOutput()} is
     *       {@code true}, stderr is merged into stdout and {@code ProcessResult.stderr()} must be empty.
     *       </li>
     *   <li>
     *       {@link ProcessResult#duration()} contains the elapsed wall-clock time if the implementation can compute it.
     *   </li>
     *   <li>
     *       If {@link ExecutionOptions#destroyProcessTree()} is {@code true}, descendants of the launched process are
     *       terminated during timeout/cancellation handling.
     *   </li>
     * </ul>
     *
     * <p><strong>Errors:</strong>
     *
     * <ul>
     *   <li>IO failures when starting the process surface as {@link RuntimeException}s (typically
     *       {@link java.io.UncheckedIOException}).</li>
     *   <li>Interruptions or pump failures that occur after launch are wrapped in
     *       {@link com.github.ulviar.icli.engine.runtime.ProcessEngineExecutionException}.</li>
     *   <li>If the supervising thread is interrupted while enforcing the shutdown plan, implementations throw
     *       {@link com.github.ulviar.icli.engine.runtime.ProcessShutdownException} after escalating to a forceful
     *       kill.</li>
     * </ul>
     *
     * <p>In all cases, implementations must restore the thread interrupt flag and attempt to terminate the process tree
     * according to the shutdown plan.</p>
     */
    ProcessResult run(CommandDefinition spec, ExecutionOptions options);

    /**
     * Start an interactive session for the command described by {@code spec}.
     *
     * <p>The returned {@link InteractiveSession} exposes stdin/stdout/stderr streams and an
     * {@link java.util.concurrent.CompletableFuture completion future}. Implementations must respect
     * {@link ExecutionOptions} for capture/timeout/shutdown policies the same way as
     * {@link #run(CommandDefinition, ExecutionOptions)}. The lifecycle of the session (idle timeout, explicit
     * shutdown) is delegated to higher layers, but the engine must ensure resources are reclaimed when the
     * {@link InteractiveSession} is closed.</p>
     *
     * <p>Terminal requirements follow the same rules as {@link #run(CommandDefinition, ExecutionOptions)}.
     * Error handling mirrors the single-run contract: IO failures bubble as {@link RuntimeException}s, supervisory
     * issues surface as {@link com.github.ulviar.icli.engine.runtime.ProcessEngineExecutionException}, and interrupted
     * shutdowns surface as {@link com.github.ulviar.icli.engine.runtime.ProcessShutdownException}.</p>
     */
    InteractiveSession startSession(CommandDefinition spec, ExecutionOptions options);
}
