package com.github.ulviar.icli.engine.pool.api.hooks;

import com.github.ulviar.icli.engine.InteractiveSession;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;

/**
 * Hook invoked after each lease to restore worker state. Hooks run synchronously on the thread returning the lease,
 * immediately after {@link com.github.ulviar.icli.engine.pool.api.WorkerLease#reset(ResetRequest)} or lease closure. The
 * hook may perform arbitrary validation or cleanup and can instruct the pool to retire the worker by returning {@link
 * ResetOutcome#RETIRE}.
 */
@FunctionalInterface
public interface ResetHook {

    /**
     * Resets or validates the worker associated with the supplied {@link LeaseScope}.
     *
     * @param session interactive session backing the worker
     * @param scope immutable lease metadata (worker id, request id, timestamps)
     * @param request context describing why the reset is being performed
     * @return {@link ResetOutcome#CONTINUE} to keep the worker in the pool or {@link ResetOutcome#RETIRE} to dispose it
     * @throws Exception when the hook encounters a fatal error; the pool retires the worker automatically
     */
    ResetOutcome reset(InteractiveSession session, LeaseScope scope, ResetRequest request) throws Exception;
}
