package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.CommandResult;
import com.github.ulviar.icli.client.InteractiveSessionClient;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import java.util.concurrent.CompletableFuture;

/**
 * Conversation wrapper that exposes both raw interactive access and line-oriented helpers.
 *
 * <p>The conversation keeps the worker leased until callers invoke {@link #close()} or {@link #retire()}.</p>
 */
public final class PooledInteractiveConversation implements AutoCloseable {

    private final InteractiveSessionClient interactiveClient;
    private final PooledLineConversationDelegate delegate;

    PooledInteractiveConversation(InteractiveSessionClient interactiveClient, PooledLineConversationDelegate delegate) {
        this.interactiveClient = interactiveClient;
        this.delegate = delegate;
    }

    /**
     * Returns the interactive client wrapping the leased worker.
     *
     * @return interactive session client
     */
    public InteractiveSessionClient interactive() {
        return interactiveClient;
    }

    /**
     * Returns the line-oriented client associated with the same worker.
     *
     * @return line session client
     */
    public LineSessionClient line() {
        return delegate.line();
    }

    /**
     * Processes a request using the line helper.
     *
     * @param input payload forwarded to the worker
     * @return command result describing success or failure
     */
    public CommandResult<String> process(String input) {
        return delegate.process(input);
    }

    /**
     * Asynchronously processes a request using the line helper.
     *
     * @param input payload forwarded to the worker
     * @return future completed with the command result
     */
    public CompletableFuture<CommandResult<String>> processAsync(String input) {
        return delegate.processAsync(input);
    }

    /**
     * Invokes the configured reset hooks for the leased worker.
     */
    public void reset() {
        delegate.reset();
    }

    /**
     * Retires the leased worker after closing the conversation.
     */
    public void retire() {
        delegate.retire();
    }

    /**
     * Exposes immutable metadata about the leased worker.
     *
     * @return lease scope describing worker and request identifiers
     */
    public LeaseScope scope() {
        return delegate.scope();
    }

    /**
     * Closes the conversation and returns the worker to the pool.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
