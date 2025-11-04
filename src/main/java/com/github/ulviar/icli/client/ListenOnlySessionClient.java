package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.ShutdownSignal;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Convenience wrapper for listen-only workflows that exposes stdout/stderr as {@link Flow.Publisher} instances while
 * retaining access to the underlying {@link InteractiveSessionClient} for lifecycle controls.
 */
public final class ListenOnlySessionClient implements AutoCloseable {

    private static final int DEFAULT_CHUNK_SIZE = 4096;

    private final InteractiveSessionClient interactive;
    private final ListenOnlyStreamPublisher stdoutPublisher;
    private final ListenOnlyStreamPublisher stderrPublisher;
    private final boolean ownsInteractive;

    private ListenOnlySessionClient(
            InteractiveSessionClient interactive, boolean ownsInteractive, int chunkSize, String descriptor) {
        this.interactive = interactive;
        this.stdoutPublisher = new ListenOnlyStreamPublisher(interactive.stdout(), chunkSize, descriptor + " stdout");
        this.stderrPublisher = new ListenOnlyStreamPublisher(interactive.stderr(), chunkSize, descriptor + " stderr");
        this.ownsInteractive = ownsInteractive;
    }

    /**
     * Creates a listen-only client that owns the underlying session. Invoking {@link #close()} shuts down the process.
     *
     * @param session interactive session supplied by the runtime
     * @return new listen-only client
     */
    public static ListenOnlySessionClient wrap(InteractiveSessionClient session) {
        return new ListenOnlySessionClient(session, true, DEFAULT_CHUNK_SIZE, "session");
    }

    /**
     * Creates a listen-only client that does <strong>not</strong> own the underlying session. Intended for pooled
     * workflows where the worker must remain running when the conversation ends.
     *
     * @param session interactive session supplied by the pool
     * @return non-owning listen-only client
     */
    public static ListenOnlySessionClient share(InteractiveSessionClient session) {
        return new ListenOnlySessionClient(session, false, DEFAULT_CHUNK_SIZE, "conversation");
    }

    /**
     * @return publisher that streams stdout chunks as {@link ByteBuffer} instances. Only a single subscriber is
     *     supported per publisher.
     */
    public Flow.Publisher<ByteBuffer> stdoutPublisher() {
        return stdoutPublisher;
    }

    /**
     * @return publisher that streams stderr chunks as {@link ByteBuffer} instances. Only a single subscriber is
     *     supported per publisher.
     */
    public Flow.Publisher<ByteBuffer> stderrPublisher() {
        return stderrPublisher;
    }

    /**
     * Exposes the underlying interactive client for advanced scenarios.
     *
     * @return interactive session client backing this listen-only view
     */
    public InteractiveSessionClient interactive() {
        return interactive;
    }

    /**
     * Returns the completion future supplied by the underlying session.
     *
     * @return future resolved with the process exit code
     */
    public CompletableFuture<Integer> onExit() {
        return interactive.onExit();
    }

    /**
     * Forwards {@link InteractiveSessionClient#closeStdin()} so callers can signal EOF if needed.
     */
    public void closeStdin() {
        interactive.closeStdin();
    }

    /**
     * Forwards {@link InteractiveSessionClient#sendSignal(ShutdownSignal)} to the underlying session.
     *
     * @param signal shutdown signal to send
     */
    public void sendSignal(ShutdownSignal signal) {
        interactive.sendSignal(signal);
    }

    /**
     * Stops streaming without closing the underlying session. Useful when pooled conversations need to return a worker
     * to the pool but keep the process running.
     */
    public void stopStreaming() {
        stdoutPublisher.close();
        stderrPublisher.close();
    }

    /**
     * Stops streaming and, when this client owns the interactive session, closes the underlying process.
     */
    @Override
    public void close() {
        stopStreaming();
        if (ownsInteractive) {
            interactive.close();
        }
    }
}
