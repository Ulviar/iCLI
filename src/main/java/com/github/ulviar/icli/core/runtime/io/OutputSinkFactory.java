package com.github.ulviar.icli.core.runtime.io;

import com.github.ulviar.icli.core.OutputCapture;
import java.nio.charset.Charset;

/** Produces {@link OutputSink} implementations that honour a requested {@link OutputCapture} policy. */
public final class OutputSinkFactory {

    /**
     * Create an {@link OutputSink} configured according to the supplied policy.
     *
     * @param policy capture policy expressed via {@link OutputCapture}
     * @return sink that enforces the requested behaviour
     * @throws UnsupportedOperationException when streaming capture is requested (not implemented yet)
     */
    public OutputSink create(OutputCapture policy) {
        if (policy instanceof OutputCapture.Bounded bounded) {
            long maxBytes = bounded.maxBytes();
            Charset charset = bounded.charset();
            return new BoundedOutputSink(maxBytes, charset);
        }
        if (policy instanceof OutputCapture.Discard) {
            return new DiscardOutputSink();
        }
        throw new UnsupportedOperationException("Streaming capture is not supported yet.");
    }
}
