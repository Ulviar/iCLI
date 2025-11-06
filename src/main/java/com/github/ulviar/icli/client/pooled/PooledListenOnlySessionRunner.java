package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.ListenOnlySessionClient;

/**
 * Runner that acquires pooled workers and exposes listen-only conversations.
 *
 * <p>The runner is thread-safe. Each call borrows a worker from the underlying pool and returns it when the
 * conversation closes.</p>
 */
public final class PooledListenOnlySessionRunner implements AutoCloseable {

    private final ProcessPoolClient client;

    /**
     * Creates a runner that borrows workers from the supplied client.
     *
     * @param client pooled client owning the worker lease lifecycle
     */
    PooledListenOnlySessionRunner(ProcessPoolClient client) {
        this.client = client;
    }

    /**
     * Opens a listen-only conversation using the service defaults.
     *
     * @return pooled listen-only conversation
     */
    public PooledListenOnlyConversation open() {
        return open(ConversationAffinity.none());
    }

    /**
     * Opens a listen-only conversation with affinity metadata.
     *
     * @param affinity affinity descriptor used to hint worker stickiness
     * @return pooled listen-only conversation
     */
    public PooledListenOnlyConversation open(ConversationAffinity affinity) {
        return openConversation(affinity);
    }

    private PooledListenOnlyConversation openConversation(ConversationAffinity affinity) {
        ServiceConversation conversation = client.openConversation(affinity);
        try {
            ListenOnlySessionClient listenOnly = ListenOnlySessionClient.share(conversation.interactive());
            return new PooledListenOnlyConversation(conversation, listenOnly);
        } catch (RuntimeException ex) {
            conversation.close();
            throw ex;
        }
    }

    /**
     * Closes the underlying pool client and releases all workers.
     */
    @Override
    public void close() {
        client.close();
    }
}
