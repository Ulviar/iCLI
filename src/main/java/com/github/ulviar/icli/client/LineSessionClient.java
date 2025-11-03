package com.github.ulviar.icli.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

/**
 * Request-response facade that runs on top of {@link InteractiveSessionClient} for line-oriented protocols.
 *
 * <p>The client sends one line of input and expects the target process to respond with a single line terminated by the
 * decoder delimiter (newline by default). It retains the underlying interactive session so callers can switch to
 * low-level streaming when needed while still benefiting from convenience helpers.</p>
 *
 * <p>Instances are obtained via {@link LineSessionRunner}; they inherit the runner's default {@link ResponseDecoder}
 * and {@link ClientScheduler}. The class is <strong>not</strong> thread-safe: call {@link #process(String)} from a
 * single thread or coordinate access externally.</p>
 */
public final class LineSessionClient implements AutoCloseable {

    private final InteractiveSessionClient delegate;
    private final ResponseDecoder responseDecoder;
    private final ClientScheduler scheduler;

    private LineSessionClient(
            InteractiveSessionClient delegate, ResponseDecoder responseDecoder, ClientScheduler scheduler) {
        this.delegate = delegate;
        this.responseDecoder = responseDecoder;
        this.scheduler = scheduler;
    }

    /**
     * Creates a new line-oriented client that decorates the supplied interactive session.
     *
     * @param session interactive session backing the line protocol
     * @param decoder decoder used to transform session output into line responses
     * @param scheduler scheduler powering asynchronous helpers such as {@link #processAsync(String)}
     * @return line session client ready for immediate use
     */
    public static LineSessionClient create(
            InteractiveSessionClient session, ResponseDecoder decoder, ClientScheduler scheduler) {
        return new LineSessionClient(session, decoder, scheduler);
    }

    /**
     * Sends {@code input} and decodes the next response according to the configured {@link ResponseDecoder}.
     *
     * @return {@link CommandResult#success(Object)} with the decoded payload or a failure containing
     * {@link LineSessionException} when IO/decoding errors occur
     */
    public CommandResult<String> process(String input) {
        try {
            delegate.sendLine(input);
            String output = responseDecoder.read(delegate.stdout(), delegate.charset());
            return CommandResult.success(output);
        } catch (UncheckedIOException | IOException e) {
            return CommandResult.failure(new LineSessionException(input, e));
        }
    }

    public void closeStdin() {
        delegate.closeStdin();
    }

    /**
     * Mirrors {@link InteractiveSessionClient#onExit()} so callers can await process shutdown when using the line
     * facade.
     */
    public CompletableFuture<Integer> onExit() {
        return delegate.onExit();
    }

    /**
     * Asynchronously invoke {@link #process(String)} using the configured {@link ClientScheduler}.
     *
     * @param input line to send to the interactive session
     *
     * @return future delivering the {@link CommandResult}
     */
    public CompletableFuture<CommandResult<String>> processAsync(String input) {
        return scheduler.submit(() -> process(input));
    }

    public Charset charset() {
        return delegate.charset();
    }

    /**
     * Exposes the underlying interactive client for advanced scenarios that require raw stream access.
     *
     * @return interactive session client backing this line-oriented facade
     */
    public InteractiveSessionClient interactive() {
        return delegate;
    }

    /**
     * Creates an expect-style helper that automates scripted prompt/response interactions using this session.
     *
     * @return new helper bound to the current session
     */
    public LineExpect expect() {
        return new LineExpect(this, delegate, responseDecoder, scheduler);
    }

    /**
     * Closes the interactive session and releases any associated operating system resources.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
