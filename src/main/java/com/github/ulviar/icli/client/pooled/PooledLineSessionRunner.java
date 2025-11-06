package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.CommandCallBuilder;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.client.ResponseDecoder;
import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import java.util.function.Consumer;

/**
 * Runner that acquires pooled workers and exposes line-oriented conversations.
 *
 * <p>The runner is thread-safe. Each call borrows a worker from the shared pool and returns it when the conversation is
 * closed. Callers must close or retire the conversation to avoid leaking the lease.</p>
 */
public final class PooledLineSessionRunner implements AutoCloseable {

    private final ProcessPoolClient client;
    private final CommandCallFactory callFactory;
    private final LineSessionFactory lineSessionFactory;
    private final ResponseDecoder defaultDecoder;

    /**
     * Creates a runner that borrows workers from the supplied pool client and uses the provided factories to create
     * command calls and line helpers.
     *
     * @param client pooled client that manages worker leases
     * @param callFactory factory responsible for generating command calls per conversation
     * @param lineSessionFactory factory for producing line helpers when a custom decoder is required
     * @param defaultDecoder decoder used when the call does not override it
     */
    PooledLineSessionRunner(
            ProcessPoolClient client,
            CommandCallFactory callFactory,
            LineSessionFactory lineSessionFactory,
            ResponseDecoder defaultDecoder) {
        this.client = client;
        this.callFactory = callFactory;
        this.lineSessionFactory = lineSessionFactory;
        this.defaultDecoder = defaultDecoder;
    }

    /**
     * Opens a conversation using the service defaults.
     *
     * @return pooled conversation bound to a worker lease
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    public PooledLineConversation open() {
        return open(ConversationAffinity.none(), callFactory.createBaseCall());
    }

    /**
     * Opens a conversation using the service defaults and supplied affinity.
     *
     * @param affinity affinity descriptor used to hint worker stickiness
     * @return pooled conversation
     */
    public PooledLineConversation open(ConversationAffinity affinity) {
        return open(affinity, callFactory.createBaseCall());
    }

    /**
     * Opens a conversation after applying the provided customisation.
     *
     * @param customiser hook used to override decoder or other call attributes
     * @return pooled conversation bound to a worker lease
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    public PooledLineConversation open(Consumer<CommandCallBuilder> customiser) {
        return open(ConversationAffinity.none(), callFactory.createCustomCall(customiser));
    }

    /**
     * Opens a conversation with custom call configuration and affinity metadata.
     *
     * @param affinity affinity descriptor used to hint worker stickiness
     * @param customiser hook used to override decoder or other call attributes
     * @return pooled conversation bound to a worker lease
     */
    public PooledLineConversation open(ConversationAffinity affinity, Consumer<CommandCallBuilder> customiser) {
        return open(affinity, callFactory.createCustomCall(customiser));
    }

    /**
     * Opens a conversation using the provided {@link CommandCall}. Intended for testing and internal reuse.
     *
     * @param call command call describing the session to open
     * @return pooled conversation bound to a worker lease
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    private PooledLineConversation open(ConversationAffinity affinity, CommandCall call) {
        ServiceConversation conversation = client.openConversation(affinity);
        try {
            LineSessionClient lineClient = resolveLineClient(conversation, call);
            PooledLineConversationDelegate delegate = new PooledLineConversationDelegate(conversation, lineClient);
            return new PooledLineConversation(delegate);
        } catch (RuntimeException ex) {
            conversation.close();
            throw ex;
        }
    }

    private LineSessionClient resolveLineClient(ServiceConversation conversation, CommandCall call) {
        ResponseDecoder decoder = call.decoder();
        if (decoder == defaultDecoder) {
            return conversation.line();
        }
        return lineSessionFactory.create(conversation.interactive(), decoder);
    }

    @Override
    public void close() {
        client.close();
    }
}
