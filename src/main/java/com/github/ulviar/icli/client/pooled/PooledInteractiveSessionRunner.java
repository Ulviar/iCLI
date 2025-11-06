package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.CommandCallBuilder;
import com.github.ulviar.icli.client.InteractiveSessionClient;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.client.ResponseDecoder;
import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import java.util.function.Consumer;

/**
 * Runner that acquires pooled workers and exposes full interactive control alongside line helpers.
 *
 * <p>The runner is thread-safe and borrows a worker for each conversation. Callers must release the lease by closing
 * or retiring the returned conversation.</p>
 */
public final class PooledInteractiveSessionRunner implements AutoCloseable {

    private final ProcessPoolClient client;
    private final CommandCallFactory callFactory;
    private final LineSessionFactory lineSessionFactory;
    private final ResponseDecoder defaultDecoder;

    /**
     * Creates a runner that borrows workers from the supplied pool client and uses the provided factories to assemble
     * command calls and line helpers.
     *
     * @param client pooled client that manages worker leases
     * @param callFactory factory responsible for creating command calls per conversation
     * @param lineSessionFactory factory for producing line-based helpers when a custom decoder is requested
     * @param defaultDecoder decoder used when callers do not override the command call
     */
    PooledInteractiveSessionRunner(
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
     * @return pooled interactive conversation
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    public PooledInteractiveConversation open() {
        return open(ConversationAffinity.none(), callFactory.createBaseCall());
    }

    /**
     * Opens a conversation with the provided affinity metadata.
     *
     * @param affinity affinity descriptor used to hint worker stickiness
     * @return pooled interactive conversation
     */
    public PooledInteractiveConversation open(ConversationAffinity affinity) {
        return open(affinity, callFactory.createBaseCall());
    }

    /**
     * Opens a conversation after applying the provided customisation.
     *
     * @param customiser hook used to override decoder or other call attributes
     * @return pooled interactive conversation
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    public PooledInteractiveConversation open(Consumer<CommandCallBuilder> customiser) {
        return open(ConversationAffinity.none(), callFactory.createCustomCall(customiser));
    }

    /**
     * Opens a conversation with custom call configuration and affinity metadata.
     *
     * @param affinity affinity descriptor used to hint worker stickiness
     * @param customiser hook used to override decoder or other call attributes
     * @return pooled interactive conversation
     */
    public PooledInteractiveConversation open(ConversationAffinity affinity, Consumer<CommandCallBuilder> customiser) {
        return open(affinity, callFactory.createCustomCall(customiser));
    }

    /**
     * Opens a conversation using the provided {@link CommandCall}. Intended for testing and internal reuse.
     *
     * @param call command call describing the session to open
     * @return pooled interactive conversation
     * @throws com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException
     *      if the pool cannot supply a worker
     * @throws RuntimeException if session initialisation fails; the conversation is closed before the exception is
     *      propagated
     */
    private PooledInteractiveConversation open(ConversationAffinity affinity, CommandCall call) {
        ServiceConversation conversation = client.openConversation(affinity);
        try {
            InteractiveSessionClient interactive = conversation.interactive();
            LineSessionClient lineClient = resolveLineClient(conversation, interactive, call);
            PooledLineConversationDelegate delegate = new PooledLineConversationDelegate(conversation, lineClient);
            return new PooledInteractiveConversation(interactive, delegate);
        } catch (RuntimeException ex) {
            conversation.close();
            throw ex;
        }
    }

    private LineSessionClient resolveLineClient(
            ServiceConversation conversation, InteractiveSessionClient interactive, CommandCall call) {
        ResponseDecoder decoder = call.decoder();
        if (decoder == defaultDecoder) {
            return conversation.line();
        }
        return lineSessionFactory.create(interactive, decoder);
    }

    @Override
    public void close() {
        client.close();
    }
}
