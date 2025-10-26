package com.github.ulviar.icli.core.runtime.io;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * In-memory sink that retains up to {@code maxBytes} bytes of process output and surfaces the truncated, decoded view.
 */
public final class BoundedOutputSink implements OutputSink {

    private final int maxBytes;
    private final Charset charset;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public BoundedOutputSink(long maxBytes, Charset charset) {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes must be between 1 and " + Integer.MAX_VALUE);
        }
        this.maxBytes = (int) maxBytes;
        this.charset = charset;
    }

    @Override
    public void append(byte[] data, int offset, int length) {
        int remaining = maxBytes - buffer.size();
        if (remaining <= 0) {
            return;
        }
        int toWrite = Math.min(remaining, length);
        buffer.write(data, offset, toWrite);
    }

    @Override
    public String content() {
        return buffer.toString(charset);
    }
}
