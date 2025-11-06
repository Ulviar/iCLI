package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.ClientScheduler;
import com.github.ulviar.icli.client.InteractiveSessionClient;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.client.ResponseDecoder;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import com.github.ulviar.icli.engine.pool.api.WorkerLease;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conversation-scoped handle that keeps a worker leased until the caller closes or retires it.
 *
 * <p>The conversation exposes both line-oriented and interactive helpers backed by the same leased worker. Callers must
 * invoke {@link #close()} or {@link #retire()} exactly once to return the worker to the pool. Listener callbacks are
 * fired in the following order: {@code conversationOpened} when the lease is acquired, {@code conversationClosing}
 * immediately before the worker is returned, {@code conversationClosed} after the lease is closed, and
 * {@code conversationReset} whenever {@link #reset()} or {@link #retire()} triggers a manual reset.</p>
 */
public final class ServiceConversation implements AutoCloseable {

    private static final ConversationReset DEFAULT_RESET = ConversationReset.manual();
    private static final ConversationReset RETIRE_RESET = ConversationReset.manual("worker retired");

    private final WorkerLease lease;
    private final LeaseScope scope;
    private final InteractiveSessionClient interactiveClient;
    private final LineSessionClient lineClient;
    private final ServiceProcessorListener listener;
    private final ConversationAffinity affinity;
    private final ConversationAffinityRegistry affinityRegistry;
    private final AtomicBoolean closed = new AtomicBoolean();

    ServiceConversation(
            WorkerLease lease, ResponseDecoder decoder, ClientScheduler scheduler, ServiceProcessorListener listener) {
        this(lease, decoder, scheduler, listener, ConversationAffinity.none(), ConversationAffinityRegistry.disabled());
    }

    ServiceConversation(
            WorkerLease lease,
            ResponseDecoder decoder,
            ClientScheduler scheduler,
            ServiceProcessorListener listener,
            ConversationAffinity affinity,
            ConversationAffinityRegistry affinityRegistry) {
        this.lease = lease;
        this.scope = lease.scope();
        this.interactiveClient = InteractiveSessionClient.wrap(lease.session());
        this.lineClient = LineSessionClient.create(interactiveClient, decoder, scheduler);
        this.listener = listener;
        this.affinity = affinity;
        this.affinityRegistry = affinityRegistry;
        listener.conversationOpened(scope);
    }

    /**
     * Returns a line-oriented client bound to the leased worker. The returned client shares ownership of the underlying
     * session and must not be closed independently; the conversation controls the lifecycle.
     *
     * @return line session client for request-response workflows
     */
    public LineSessionClient line() {
        return lineClient;
    }

    /**
     * Returns the interactive client exposing raw streams for the leased worker. Callers must not close the returned
     * client directly—instead close or retire the conversation.
     *
     * @return interactive client with direct access to stdin/stdout/stderr
     */
    public InteractiveSessionClient interactive() {
        return interactiveClient;
    }

    /**
     * Manually resets the leased worker by invoking the configured reset hooks.
     *
     * <p>The call is useful when the conversation executes multiple loosely related commands and needs to restore the
     * worker state between them. The conversation remains open; callers must still invoke {@link #close()} or
     * {@link #retire()} when finished.</p>
     */
    public void reset() {
        reset(DEFAULT_RESET);
    }

    /**
     * Manually resets the leased worker using the provided metadata.
     *
     * @param reset descriptor explaining why the reset occurred
     */
    public void reset(ConversationReset reset) {
        lease.reset(ResetRequest.manual(scope.requestId()));
        listener.conversationReset(scope, reset);
    }

    /**
     * Exposes immutable metadata about the leased worker. The returned scope is the same instance supplied by the
     * underlying {@link WorkerLease}.
     *
     * @return conversation scope describing worker id, request id, and timestamps
     */
    public LeaseScope scope() {
        return scope;
    }

    /**
     * Returns the worker to the pool without retiring it. The method is idempotent: subsequent invocations have no
     * effect once the lease has been closed.
     */
    @Override
    public void close() {
        closeInternal(CloseDirective.closeNormally());
    }

    /**
     * Retires the worker after closing the conversation. The underlying session is closed and the pool is instructed to
     * launch a replacement before serving subsequent requests. This method is idempotent; repeated invocations are
     * ignored after the first close.
     */
    public void retire() {
        retire(ConversationRetirement.unspecified());
    }

    /**
     * Retires the worker with custom metadata.
     *
     * @param retirement descriptor explaining why the worker is being retired
     */
    public void retire(ConversationRetirement retirement) {
        closeInternal(CloseDirective.retire(retirement));
    }

    private void closeInternal(CloseDirective directive) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        listener.conversationClosing(scope);
        try {
            if (directive.retire()) {
                lease.reset(ResetRequest.retire(scope.requestId()));
                listener.conversationReset(scope, RETIRE_RESET);
                listener.conversationRetired(scope, directive.retirement());
                interactiveClient.close();
            }
        } finally {
            lease.close();
            updateAffinity(directive.retire());
            listener.conversationClosed(scope);
        }
    }

    private void updateAffinity(boolean retired) {
        if (!affinity.isPresent()) {
            return;
        }
        if (retired) {
            affinityRegistry.forget(affinity);
            return;
        }
        affinityRegistry.remember(affinity, scope.workerId());
    }
}
