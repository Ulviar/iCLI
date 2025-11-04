package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.ListenOnlySessionClient;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;

/**
 * Conversation wrapper tailored for listen-only workflows on pooled workers. Streams are stopped automatically when the
 * conversation closes or retires, but the underlying worker remains running unless {@link #retire()} is invoked.
 */
public final class PooledListenOnlyConversation implements AutoCloseable {

    private final ServiceConversation conversation;
    private final ListenOnlySessionClient listenOnlyClient;

    /**
     * @param conversation pooled conversation that governs the worker lease
     * @param listenOnlyClient listen-only view bound to the leased worker
     */
    PooledListenOnlyConversation(ServiceConversation conversation, ListenOnlySessionClient listenOnlyClient) {
        this.conversation = conversation;
        this.listenOnlyClient = listenOnlyClient;
    }

    /**
     * Returns the listen-only client bound to the leased worker.
     *
     * @return listen-only client
     */
    public ListenOnlySessionClient listenOnly() {
        return listenOnlyClient;
    }

    /**
     * Invokes the configured reset hooks for the leased worker.
     */
    public void reset() {
        conversation.reset();
    }

    /**
     * Retires the leased worker after closing the conversation.
     */
    public void retire() {
        listenOnlyClient.stopStreaming();
        conversation.retire();
    }

    /**
     * Exposes immutable metadata about the leased worker.
     *
     * @return lease scope describing worker and request identifiers
     */
    public LeaseScope scope() {
        return conversation.scope();
    }

    /**
     * Closes the conversation and returns the worker to the pool.
     */
    @Override
    public void close() {
        listenOnlyClient.stopStreaming();
        conversation.close();
    }
}
