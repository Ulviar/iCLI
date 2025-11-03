package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.CommandResult;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.client.ServiceConversation;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import java.util.concurrent.CompletableFuture;

/**
 * Shared delegate that encapsulates the reusable behaviours exposed by pooled conversation wrappers.
 */
final class PooledLineConversationDelegate implements AutoCloseable {

    private final ServiceConversation conversation;
    private final LineSessionClient lineClient;

    /**
     * Creates a delegate bound to the supplied conversation and line client.
     *
     * @param conversation pooled conversation controlling the worker lease
     * @param lineClient line-oriented helper derived from the conversation
     */
    PooledLineConversationDelegate(ServiceConversation conversation, LineSessionClient lineClient) {
        this.conversation = conversation;
        this.lineClient = lineClient;
    }

    /**
     * Returns the line-oriented helper associated with the leased worker.
     *
     * @return line session client bound to the delegate conversation
     */
    LineSessionClient line() {
        return lineClient;
    }

    /**
     * Processes a request using the delegate line client.
     *
     * @param input payload forwarded to the worker
     * @return command result describing success or failure
     */
    CommandResult<String> process(String input) {
        return lineClient.process(input);
    }

    /**
     * Asynchronously processes a request using the delegate line client.
     *
     * @param input payload forwarded to the worker
     * @return future completed with the command result
     */
    CompletableFuture<CommandResult<String>> processAsync(String input) {
        return lineClient.processAsync(input);
    }

    /**
     * Invokes the reset hooks for the leased worker via the underlying conversation.
     */
    void reset() {
        conversation.reset();
    }

    /**
     * Retires the leased worker after closing the conversation.
     */
    void retire() {
        conversation.retire();
    }

    /**
     * Exposes immutable metadata about the leased worker.
     *
     * @return lease scope describing worker and request identifiers
     */
    LeaseScope scope() {
        return conversation.scope();
    }

    /**
     * Closes the conversation and returns the worker to the pool.
     */
    @Override
    public void close() {
        conversation.close();
    }
}
