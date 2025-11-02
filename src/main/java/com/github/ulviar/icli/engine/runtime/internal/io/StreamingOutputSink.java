package com.github.ulviar.icli.engine.runtime.internal.io;

import com.github.ulviar.icli.engine.diagnostics.DiagnosticsEvent;
import com.github.ulviar.icli.engine.diagnostics.DiagnosticsListener;
import com.github.ulviar.icli.engine.diagnostics.StreamType;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Streaming sink that forwards every observed chunk to diagnostics listeners without retaining any data in memory.
 */
public final class StreamingOutputSink implements OutputSink {

    private final Charset charset;
    private final StreamType stream;
    private final DiagnosticsListener diagnostics;

    public StreamingOutputSink(Charset charset, StreamType stream, DiagnosticsListener diagnostics) {
        this.charset = charset;
        this.stream = stream;
        this.diagnostics = diagnostics;
    }

    @Override
    public void append(byte[] buffer, int offset, int length) {
        if (length <= 0) {
            return;
        }
        byte[] payload = Arrays.copyOfRange(buffer, offset, offset + length);
        diagnostics.onEvent(new DiagnosticsEvent.OutputChunk(stream, payload, charset));
    }

    @Override
    public String content() {
        return "";
    }
}
