package com.github.ulviar.icli.core.runtime.io;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Drains a process stream (stdout or stderr) into an {@link OutputSink}. Implementations decide how to schedule the
 * work (virtual threads, executors, etc.) but must guarantee forward progress so child processes cannot block on full
 * pipes.
 */
public interface StreamDrainer {

    /**
     * Begin draining {@code source} asynchronously into {@code sink}. Implementations must close {@code source} when the
     * transfer completes or fails.
     *
     * @param source stream produced by the child process
     * @param sink destination that accumulates or forwards the bytes
     * @return future that completes when EOF is reached or fails if an I/O error occurs
     */
    CompletableFuture<Void> drain(InputStream source, OutputSink sink);
}
