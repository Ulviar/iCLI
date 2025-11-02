package com.github.ulviar.icli.engine;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a live, interactive process launched by the runtime.
 *
 * <p>The session exposes raw streams plus lifecycle controls so callers can implement REPL-style protocols and
 * high-level client conveniences. Instances are stateful but thread-safe for typical usage patterns: writing to
 * {@link #stdin()} and consuming {@link #stdout()} or {@link #stderr()} concurrently from different threads is
 * supported. Most implementations close automatically when the underlying process exits; callers are still responsible
 * for disposing of unused sessions by invoking {@link #close()}.
 *
 * <p>Sessions honour the policies configured through {@link ExecutionOptions}, including idle timeout enforcement and
 * lifecycle notifications via {@link SessionLifecycleObserver}. Methods documented below describe how to interact with
 * the underlying process once the session handle has been obtained from {@code ProcessEngine.startSession} or higher
 * level facades such as {@code CommandService.interactiveSessionRunner().open(...)}.
 */
public interface InteractiveSession extends AutoCloseable {

    /**
     * Provides the writable stream connected to the process stdin.
     *
     * @return the output stream that forwards bytes to the child process. Callers should flush after each logical write
     * and avoid closing the stream directly; prefer {@link #closeStdin()} to signal EOF.
     */
    OutputStream stdin();

    /**
     * Provides the readable stream connected to the process stdout.
     *
     * @return the input stream yielding bytes emitted by stdout. The stream remains open until the process exits or the
     * runtime forcibly terminates the session.
     */
    InputStream stdout();

    /**
     * Provides the readable stream connected to the process stderr.
     *
     * @return the input stream yielding bytes emitted by stderr. Depending on {@link ExecutionOptions}, stderr may be
     * drained concurrently even when callers ignore the stream.
     */
    InputStream stderr();

    /**
     * Exposes the exit status future for the child process.
     *
     * @return a {@link CompletableFuture} that completes with the process exit code. Cancellation propagates a best
     * effort termination to the process according to the configured {@link ShutdownPlan}.
     */
    CompletableFuture<Integer> onExit();

    /**
     * Half-closes the session by shutting down stdin while leaving the process and output streams intact.
     *
     * <p>Use this method when the child expects EOF to finish processing (for example, scripting languages that exit
     * after consuming standard input) but you still need to collect final output or the exit status.
     */
    void closeStdin();

    /**
     * Sends a control signal to the child process.
     *
     * @param signal the shutdown signal to deliver, such as {@link ShutdownSignal#INTERRUPT} or
     *               {@link ShutdownSignal#KILL}. Implementations translate this into the appropriate platform-specific
     *               mechanism (for instance, writing Ctrl+C on PTY sessions or invoking {@code Process.destroy()}).
     */
    void sendSignal(ShutdownSignal signal);

    /**
     * Resizes the attached pseudo terminal, if present.
     *
     * <p>Pipe-backed sessions treat this call as a no-op. Implementations typically ignore non-positive dimensions
     * because underlying PTY providers require strictly positive sizes.
     *
     * @param columns the new terminal width in characters
     * @param rows    the new terminal height in rows
     */
    void resizePty(int columns, int rows);

    /**
     * Closes the session and tears down the underlying process.
     *
     * <p>This method is idempotent. Implementations follow the configured {@link ShutdownPlan} when terminating the
     * process and close any remaining open streams. Callers should always invoke {@code close()} when they are done with
     * the session to release operating system resources promptly.
     */
    @Override
    void close();
}
