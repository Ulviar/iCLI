package com.github.ulviar.icli.engine.runtime.internal.io;

/**
 * Contract for accumulating process output emitted by the child process. Implementations can retain the bytes in memory
 * (for bounded summaries), stream them elsewhere, or discard them entirely depending on the configured capture mode.
 */
public interface OutputSink {

    /**
     * Consume a chunk of bytes read from stdout/stderr.
     *
     * @param buffer source array containing process output
     * @param offset starting position within {@code buffer}
     * @param length number of bytes to read from {@code buffer}
     */
    void append(byte[] buffer, int offset, int length);

    /**
     * @return the textual representation accumulated so far, using the sink's configured charset/semantics.
     */
    String content();
}
