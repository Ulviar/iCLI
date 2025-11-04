package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.CommandResult;
import com.github.ulviar.icli.client.ResponseDecoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper that delegates pooled request/response workflows to a shared {@link ServiceProcessor}.
 */
public final class PooledCommandRunner implements AutoCloseable {

    private final ProcessPoolClient client;
    private final ResponseDecoder decoder;
    private final ServiceProcessor processor;
    private final CloseAction closeAction;

    /**
     * Creates a runner bound to the provided pool client and decoder.
     *
     * @param client pooled client responsible for borrowing workers
     * @param decoder decoder used for responses produced by {@link #process(String)} and {@link #processAsync(String)}
     */
    PooledCommandRunner(ProcessPoolClient client, ResponseDecoder decoder) {
        this(new CloseAction(client), client, decoder, client.serviceProcessor(decoder));
    }

    private PooledCommandRunner(
            CloseAction closeAction, ProcessPoolClient client, ResponseDecoder decoder, ServiceProcessor processor) {
        this.closeAction = closeAction;
        this.client = client;
        this.decoder = decoder;
        this.processor = processor;
    }

    /**
     * Processes the supplied input using a pooled worker.
     *
     * @param input payload forwarded to the worker
     * @return command result describing success or failure
     */
    public CommandResult<String> process(String input) {
        return processor.process(input);
    }

    /**
     * Asynchronously processes the supplied input using the shared scheduler.
     *
     * @param input payload forwarded to the worker
     * @return future completed with the command result
     */
    public CompletableFuture<CommandResult<String>> processAsync(String input) {
        return processor.processAsync(input);
    }

    /**
     * Returns a runner that decodes responses using the provided {@link ResponseDecoder} while sharing the same pool.
     * If the decoder matches the current runner, {@code this} is returned.
     *
     * @param decoder response decoder to apply
     * @return runner bound to the decoder
     */
    public PooledCommandRunner withDecoder(ResponseDecoder decoder) {
        if (this.decoder == decoder) {
            return this;
        }
        return new PooledCommandRunner(closeAction.retain(), client, decoder, client.serviceProcessor(decoder));
    }

    /**
     * Returns the decoder currently associated with this runner.
     *
     * @return decoder used to interpret worker responses
     */
    ResponseDecoder decoder() {
        return decoder;
    }

    @Override
    public void close() {
        closeAction.close();
    }

    private record CloseAction(ProcessPoolClient client, AtomicBoolean closed) {

        CloseAction(ProcessPoolClient client) {
            this(client, new AtomicBoolean(false));
        }

        CloseAction retain() {
            return new CloseAction(client, closed);
        }

        void close() {
            if (closed.compareAndSet(false, true)) {
                client.close();
            }
        }
    }
}
