package com.github.ulviar.icli.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

/** Request-response interactive session facade (one input line → one output string). */
public final class LineSessionClient implements AutoCloseable {
    private static final ResponseDecoder DEFAULT_DECODER = new LineDelimitedResponseDecoder();

    private final InteractiveSessionClient delegate;
    private final ResponseDecoder responseDecoder;

    private LineSessionClient(InteractiveSessionClient delegate, ResponseDecoder responseDecoder) {
        this.delegate = delegate;
        this.responseDecoder = responseDecoder;
    }

    static LineSessionClient create(InteractiveSessionClient session) {
        return new LineSessionClient(session, DEFAULT_DECODER);
    }

    static LineSessionClient create(InteractiveSessionClient session, ResponseDecoder decoder) {
        return new LineSessionClient(session, decoder);
    }

    public CommandResult<String> process(String input) {
        try {
            delegate.sendLine(input);
            String output = responseDecoder.read(delegate.stdout(), delegate.charset());
            return CommandResult.success(output);
        } catch (UncheckedIOException | IOException e) {
            return CommandResult.failure(e);
        }
    }

    public void closeStdin() {
        delegate.closeStdin();
    }

    public CompletableFuture<Integer> onExit() {
        return delegate.onExit();
    }

    public Charset charset() {
        return delegate.charset();
    }

    public InteractiveSessionClient interactive() {
        return delegate;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
