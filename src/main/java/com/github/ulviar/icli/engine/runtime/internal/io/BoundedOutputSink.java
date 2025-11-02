package com.github.ulviar.icli.engine.runtime.internal.io;

import com.github.ulviar.icli.engine.diagnostics.DiagnosticsEvent;
import com.github.ulviar.icli.engine.diagnostics.DiagnosticsListener;
import com.github.ulviar.icli.engine.diagnostics.StreamType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * In-memory sink that retains up to {@code maxBytes} bytes of process output and surfaces the truncated, decoded view.
 */
public final class BoundedOutputSink implements OutputSink {

    private final int maxBytes;
    private final Charset charset;
    private final StreamType stream;
    private final DiagnosticsListener diagnostics;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public BoundedOutputSink(long maxBytes, Charset charset, StreamType stream, DiagnosticsListener diagnostics) {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes must be between 1 and " + Integer.MAX_VALUE);
        }
        this.maxBytes = (int) maxBytes;
        this.charset = charset;
        this.stream = stream;
        this.diagnostics = diagnostics;
    }

    @Override
    public void append(byte[] data, int offset, int length) {
        if (length <= 0) {
            return;
        }
        int remaining = maxBytes - buffer.size();
        if (remaining <= 0) {
            emitTruncated(data, offset, length);
            return;
        }
        int toWrite = Math.min(remaining, length);
        buffer.write(data, offset, toWrite);
        if (length > toWrite) {
            emitTruncated(data, offset + toWrite, length - toWrite);
        }
    }

    @Override
    public String content() {
        return buffer.toString(charset);
    }

    private void emitTruncated(byte[] data, int offset, int discardedLength) {
        if (discardedLength <= 0) {
            return;
        }
        byte[] preview = Arrays.copyOfRange(data, offset, offset + discardedLength);
        diagnostics.onEvent(
                new DiagnosticsEvent.OutputTruncated(stream, preview, charset, buffer.size(), discardedLength));
    }
}
