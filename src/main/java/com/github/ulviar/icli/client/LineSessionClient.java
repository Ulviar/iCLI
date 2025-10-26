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
    private final ClientScheduler scheduler;

    private LineSessionClient(
            InteractiveSessionClient delegate, ResponseDecoder responseDecoder, ClientScheduler scheduler) {
        this.delegate = delegate;
        this.responseDecoder = responseDecoder;
        this.scheduler = scheduler;
    }

    static LineSessionClient create(
            InteractiveSessionClient session, ResponseDecoder decoder, ClientScheduler scheduler) {
        return new LineSessionClient(session, decoder, scheduler);
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

    /**
     * Asynchronously invoke {@link #process(String)} using the configured {@link ClientScheduler}.
     *
     * @param input line to send to the interactive session
     * @return future delivering the {@link CommandResult}
     */
    public CompletableFuture<CommandResult<String>> processAsync(String input) {
        return scheduler.submit(() -> process(input));
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
