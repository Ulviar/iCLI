package com.github.ulviar.icli.core.runtime.io;

import com.github.ulviar.icli.core.OutputCapture;
import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsListener;
import com.github.ulviar.icli.core.runtime.diagnostics.StreamType;
import java.nio.charset.Charset;

/**
 * Produces {@link OutputSink} implementations that honour a requested {@link OutputCapture} policy.
 */
public final class OutputSinkFactory {

    /**
     * Create an {@link OutputSink} configured according to the supplied policy.
     *
     * @param policy      capture policy expressed via {@link OutputCapture}
     * @param stream      originating stream used for diagnostics
     * @param diagnostics listener notified of streaming/truncation events
     *
     * @return sink that enforces the requested behaviour
     */
    public OutputSink create(OutputCapture policy, StreamType stream, DiagnosticsListener diagnostics) {
        switch (policy) {
            case OutputCapture.Bounded bounded -> {
                long maxBytes = bounded.maxBytes();
                Charset charset = bounded.charset();
                return new BoundedOutputSink(maxBytes, charset, stream, diagnostics);
            }
            case OutputCapture.Streaming streaming -> {
                Charset charset = streaming.charset();
                return new StreamingOutputSink(charset, stream, diagnostics);
            }
            case OutputCapture.Discard _ -> {
                return new DiscardOutputSink();
            }
            default -> {}
        }
        throw new UnsupportedOperationException("Unknown output capture policy: " + policy);
    }
}
