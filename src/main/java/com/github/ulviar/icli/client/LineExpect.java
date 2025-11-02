package com.github.ulviar.icli.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Expect-style helper that automates scripted prompt/response interactions on top of a {@link LineSessionClient}. The
 * helper decodes stdout using the session's configured {@link ResponseDecoder}, pipes input through the
 * {@link InteractiveSessionClient}, and lets callers describe conversations using fluent {@code send}/{@code expect}
 * methods.
 *
 * <p>The helper honours a configurable default timeout applied to every subsequent expectation. A timeout of
 * {@link Duration#ZERO zero} disables waiting limits and blocks until the process produces a matching line or the
 * stream closes. Positive timeouts run the decoder on the session's {@link ClientScheduler} and raise
 * {@link LineExpectationTimeoutException} when the deadline elapses without producing a response.</p>
 *
 * <p>Instances are <strong>not</strong> thread-safe. Callers should execute scripted interactions from a single thread
 * to preserve ordering guarantees.</p>
 */
public final class LineExpect implements AutoCloseable {

    private final LineSessionClient client;
    private final InteractiveSessionClient interactive;
    private final ResponseDecoder decoder;
    private final ClientScheduler scheduler;
    private final InputStream stdout;
    private final Charset charset;
    private Duration defaultTimeout = Duration.ZERO;

    LineExpect(
            LineSessionClient client,
            InteractiveSessionClient interactive,
            ResponseDecoder decoder,
            ClientScheduler scheduler) {
        this.client = client;
        this.interactive = interactive;
        this.decoder = decoder;
        this.scheduler = scheduler;
        this.stdout = interactive.stdout();
        this.charset = interactive.charset();
    }

    /**
     * Overrides the default timeout used by {@link #expectLine(String)} and {@link #expectMatches(Pattern)}.
     *
     * @param timeout timeout applied to subsequent expectations; {@link Duration#ZERO} disables the timeout entirely
     * @return this helper to allow fluent chaining
     * @throws IllegalArgumentException when {@code timeout} is negative
     */
    public LineExpect withDefaultTimeout(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        defaultTimeout = timeout;
        return this;
    }

    /**
     * Sends a line to the underlying session.
     *
     * @param input line sent without the trailing delimiter
     * @return this helper to allow fluent chaining
     * @throws LineExpectationException when writing to stdin fails
     */
    public LineExpect send(String input) {
        try {
            interactive.sendLine(input);
        } catch (UncheckedIOException ex) {
            throw new LineExpectationException(String.format(Locale.ROOT, "Failed to send line \"%s\"", input), ex);
        }
        return this;
    }

    /**
     * Reads the next decoded line and verifies it matches {@code expected}.
     *
     * @param expected expected line
     * @return the decoded line returned by the process
     * @throws LineExpectationException when the next line differs from {@code expected} or cannot be read
     */
    public String expectLine(String expected) {
        String actual = readNext(defaultTimeout);
        if (!expected.equals(actual)) {
            throw new LineExpectationException(
                    String.format(Locale.ROOT, "Expected \"%s\" but received \"%s\"", expected, actual));
        }
        return actual;
    }

    /**
     * Reads the next decoded line without applying validation.
     *
     * @return the decoded line returned by the process
     * @throws LineExpectationException when the line cannot be read before the timeout elapses or the stream closes
     */
    public String expectAny() {
        return readNext(defaultTimeout);
    }

    /**
     * Reads the next decoded line and verifies it matches {@code pattern}.
     *
     * @param pattern pattern applied to the decoded line
     * @return the decoded line returned by the process
     * @throws LineExpectationException when the next line does not satisfy {@code pattern} or cannot be read
     */
    public String expectMatches(Pattern pattern) {
        String actual = readNext(defaultTimeout);
        if (!pattern.matcher(actual).matches()) {
            throw new LineExpectationException(
                    String.format(Locale.ROOT, "Line \"%s\" did not match pattern %s", actual, pattern));
        }
        return actual;
    }

    /**
     * Sends a line and verifies the immediate response equals {@code expected}.
     *
     * @param input line to send
     * @param expected expected response
     * @return this helper to allow fluent chaining
     * @throws LineExpectationException when sending fails or the response is different from {@code expected}
     */
    public LineExpect sendAndExpect(String input, String expected) {
        return send(input).tap(() -> expectLine(expected));
    }

    /**
     * Sends a line and verifies the immediate response matches {@code pattern}.
     *
     * @param input line to send
     * @param pattern pattern applied to the response
     * @return this helper to allow fluent chaining
     * @throws LineExpectationException when sending fails or the response does not satisfy {@code pattern}
     */
    public LineExpect sendAndExpectMatches(String input, Pattern pattern) {
        return send(input).tap(() -> expectMatches(pattern));
    }

    /**
     * Half-closes stdin for the session.
     *
     * @return this helper to allow fluent chaining
     */
    public LineExpect closeStdin() {
        client.closeStdin();
        return this;
    }

    /**
     * Exposes the underlying session client for advanced scenarios.
     *
     * @return the {@link LineSessionClient} backing scripted interactions
     */
    public LineSessionClient client() {
        return client;
    }

    /**
     * Closes the helper and the underlying line session.
     */
    @Override
    public void close() {
        client.close();
    }

    private LineExpect tap(Runnable runnable) {
        runnable.run();
        return this;
    }

    private String readNext(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (timeout.isZero()) {
            return readLine();
        }

        CompletableFuture<String> future = scheduler.submit(this::readLine);
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new LineExpectationTimeoutException(timeout, "next line");
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LineExpectationException("Interrupted while waiting for line", ex);
        } catch (ExecutionException ex) {
            throw propagateFailure(ex.getCause());
        }
    }

    private String readLine() {
        try {
            return decoder.read(stdout, charset);
        } catch (IOException ex) {
            throw new LineExpectationException("Failed to read line", ex);
        }
    }

    private RuntimeException propagateFailure(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new LineExpectationException("Failed to read line", cause);
    }
}
