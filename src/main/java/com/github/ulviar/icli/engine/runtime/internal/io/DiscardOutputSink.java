package com.github.ulviar.icli.engine.runtime.internal.io;

/**
 * Output sink that deliberately ignores every byte. Useful when the caller selects {@code OutputCapture.discard()} and
 * only cares about exit status without consuming additional memory.
 */
public final class DiscardOutputSink implements OutputSink {

    @Override
    public void append(byte[] buffer, int offset, int length) {
        // intentionally discarded
    }

    @Override
    public String content() {
        return "";
    }
}
