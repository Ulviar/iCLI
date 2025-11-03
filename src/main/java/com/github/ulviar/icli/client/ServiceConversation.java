package com.github.ulviar.icli.client;

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

    private final WorkerLease lease;
    private final LeaseScope scope;
    private final InteractiveSessionClient interactiveClient;
    private final LineSessionClient lineClient;
    private final ServiceProcessorListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    ServiceConversation(
            WorkerLease lease, ResponseDecoder decoder, ClientScheduler scheduler, ServiceProcessorListener listener) {
        this.lease = lease;
        this.scope = lease.scope();
        this.interactiveClient = InteractiveSessionClient.wrap(lease.session());
        this.lineClient = LineSessionClient.create(interactiveClient, decoder, scheduler);
        this.listener = listener;
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
        lease.reset(ResetRequest.manual(scope.requestId()));
        listener.conversationReset(scope);
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
        closeInternal(false);
    }

    /**
     * Retires the worker after closing the conversation. The underlying session is closed and the pool is instructed to
     * launch a replacement before serving subsequent requests. This method is idempotent; repeated invocations are
     * ignored after the first close.
     */
    public void retire() {
        closeInternal(true);
    }

    private void closeInternal(boolean retire) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        listener.conversationClosing(scope);
        try {
            if (retire) {
                lease.reset(ResetRequest.retire(scope.requestId()));
                listener.conversationReset(scope);
                interactiveClient.close();
            }
        } finally {
            lease.close();
            listener.conversationClosed(scope);
        }
    }
}
