package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.InteractiveSession;
import com.github.ulviar.icli.engine.ShutdownSignal;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * High-level convenience wrapper around a live {@link InteractiveSession}.
 *
 * <p>Instances expose the same raw streams and lifecycle controls as the underlying handle while adding ergonomic
 * helpers such as {@link #sendLine(String)} and charset-aware writing. The default factory installs UTF-8, mirroring
 * the library-wide text encoding, but callers inside the {@code client} package may supply a custom {@link Charset}
 * when working with legacy processes.
 *
 * <p>Clients typically obtain an instance via {@link InteractiveSessionRunner#open()} or its overloads. The wrapper is
 * a thin facade: it does not introduce additional buffering, guarantees the same thread-safety characteristics as the
 * wrapped session, and implements {@link AutoCloseable} so callers can dispose of resources using try-with-resources.
 */
public final class InteractiveSessionClient implements AutoCloseable {
    private final InteractiveSession handle;
    private final Charset charset;

    private InteractiveSessionClient(InteractiveSession handle, Charset charset) {
        this.handle = handle;
        this.charset = charset;
    }

    /**
     * Creates a client that uses UTF-8 when writing text to stdin.
     *
     * @param handle interactive session returned by the runtime
     * @return new client forwarding operations to the provided handle
     */
    static InteractiveSessionClient wrap(InteractiveSession handle) {
        return new InteractiveSessionClient(handle, StandardCharsets.UTF_8);
    }

    /**
     * Creates a client with a custom charset for text helpers.
     *
     * @param handle  interactive session returned by the runtime
     * @param charset charset used by {@link #sendLine(String)}
     * @return new client forwarding operations to the provided handle
     */
    static InteractiveSessionClient wrap(InteractiveSession handle, Charset charset) {
        return new InteractiveSessionClient(handle, charset);
    }

    /**
     * Exposes the underlying session for advanced scenarios.
     *
     * @return live {@link InteractiveSession}
     */
    public InteractiveSession handle() {
        return handle;
    }

    /**
     * Reports the charset used by text helpers.
     *
     * @return charset associated with {@link #sendLine(String)}
     */
    public Charset charset() {
        return charset;
    }

    /**
     * Provides direct access to process stdin.
     *
     * @return writable stream connected to the child stdin
     */
    public OutputStream stdin() {
        return handle.stdin();
    }

    /**
     * Provides direct access to process stdout.
     *
     * @return readable stream connected to the child stdout
     */
    public InputStream stdout() {
        return handle.stdout();
    }

    /**
     * Provides direct access to process stderr.
     *
     * @return readable stream connected to the child stderr
     */
    public InputStream stderr() {
        return handle.stderr();
    }

    /**
     * Mirrors the process completion future.
     *
     * @return future that completes with the exit code; cancelling the future triggers the configured shutdown plan
     */
    public CompletableFuture<Integer> onExit() {
        return handle.onExit();
    }

    /**
     * Writes a line to the session stdin using the configured charset.
     *
     * <p>The method appends {@code '\n'} to the supplied payload and flushes the stream to keep interactive prompts
     * responsive.
     *
     * @param line text payload to send without the trailing newline
     * @throws UncheckedIOException when the underlying output stream reports an {@link IOException}
     */
    public void sendLine(String line) {
        try {
            OutputStream stdin = handle.stdin();
            stdin.write(line.getBytes(charset));
            stdin.write('\n');
            stdin.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to send line", e);
        }
    }

    /**
     * Half-closes stdin while leaving the session running.
     *
     * <p>Delegates to {@link InteractiveSession#closeStdin()} so callers can request EOF without destroying the process.
     */
    public void closeStdin() {
        handle.closeStdin();
    }

    /**
     * Resizes the attached pseudo terminal, if present.
     *
     * @param columns terminal width in characters
     * @param rows    terminal height in rows
     *
     * @see InteractiveSession#resizePty(int, int)
     */
    public void resizePty(int columns, int rows) {
        handle.resizePty(columns, rows);
    }

    /**
     * Sends a shutdown or control signal to the process.
     *
     * @param signal signal translated according to {@link InteractiveSession#sendSignal(ShutdownSignal)}
     */
    public void sendSignal(ShutdownSignal signal) {
        handle.sendSignal(signal);
    }

    /**
     * Closes the session and releases the underlying process resources.
     *
     * @see InteractiveSession#close()
     */
    @Override
    public void close() {
        handle.close();
    }
}
