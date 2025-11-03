package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.pool.api.LeaseScope;

/**
 * Observer for high-level pooled request events. Implementations are invoked synchronously on the calling thread and
 * must avoid blocking or throwing.
 */
public interface ServiceProcessorListener {

    /**
     * Returns a listener that ignores every event.
     *
     * @return no-op listener
     */
    static ServiceProcessorListener noOp() {
        return new ServiceProcessorListener() {};
    }

    /**
     * Fired immediately after a lease is acquired and the request is about to be dispatched to the worker.
     *
     * @param scope metadata for the leased worker
     * @param input payload supplied to {@code process()}
     */
    default void requestStarted(LeaseScope scope, String input) {}

    /**
     * Fired when a request completes successfully.
     *
     * @param scope metadata for the leased worker
     * @param result successful command result
     */
    default void requestCompleted(LeaseScope scope, CommandResult<String> result) {}

    /**
     * Fired when a request fails, either because {@code CommandResult} contains an error or due to an unexpected
     * exception.
     *
     * @param scope metadata for the leased worker
     * @param error cause of the failure
     */
    default void requestFailed(LeaseScope scope, Throwable error) {}

    /**
     * Fired when a conversation-scope handle is created.
     *
     * @param scope metadata for the leased worker
     */
    default void conversationOpened(LeaseScope scope) {}

    /**
     * Fired immediately before a conversation closes and the lease is returned to the pool.
     *
     * @param scope metadata for the leased worker
     */
    default void conversationClosing(LeaseScope scope) {}

    /**
     * Fired after a conversation closes and the lease has been returned to the pool.
     *
     * @param scope metadata for the leased worker
     */
    default void conversationClosed(LeaseScope scope) {}

    /**
     * Fired when client code explicitly resets a conversation via {@code ServiceConversation.reset()}.
     *
     * @param scope metadata for the leased worker
     */
    default void conversationReset(LeaseScope scope) {}
}
