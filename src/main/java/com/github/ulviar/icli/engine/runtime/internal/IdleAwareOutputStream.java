package com.github.ulviar.icli.engine.runtime.internal;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream decorator that invokes a callback after every write/flush/close operation to signal activity.
 */
final class IdleAwareOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final Runnable activityCallback;

    IdleAwareOutputStream(OutputStream delegate, Runnable activityCallback) {
        this.delegate = delegate;
        this.activityCallback = activityCallback;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
        activityCallback.run();
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
        activityCallback.run();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
        activityCallback.run();
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
        activityCallback.run();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        activityCallback.run();
    }
}
