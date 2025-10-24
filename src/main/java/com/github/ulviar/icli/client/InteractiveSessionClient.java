package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.InteractiveSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** High-level wrapper around {@link InteractiveSession}. */
public final class InteractiveSessionClient implements AutoCloseable {
    private final InteractiveSession handle;
    private final Charset charset;

    private InteractiveSessionClient(InteractiveSession handle, Charset charset) {
        this.handle = handle;
        this.charset = charset;
    }

    static InteractiveSessionClient wrap(InteractiveSession handle) {
        return new InteractiveSessionClient(handle, StandardCharsets.UTF_8);
    }

    static InteractiveSessionClient wrap(InteractiveSession handle, Charset charset) {
        return new InteractiveSessionClient(handle, charset);
    }

    public InteractiveSession handle() {
        return handle;
    }

    public Charset charset() {
        return charset;
    }

    public OutputStream stdin() {
        return handle.stdin();
    }

    public InputStream stdout() {
        return handle.stdout();
    }

    public InputStream stderr() {
        return handle.stderr();
    }

    public CompletableFuture<Integer> onExit() {
        return handle.onExit();
    }

    public void sendLine(String line) {
        try {
            OutputStream stdin = handle.stdin();
            stdin.write(line.getBytes(charset));
            stdin.write('\n');
            stdin.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to send line", e);
        }
    }

    public void closeStdin() {
        try {
            handle.stdin().close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close stdin", e);
        }
    }

    @Override
    public void close() {
        handle.close();
    }
}
