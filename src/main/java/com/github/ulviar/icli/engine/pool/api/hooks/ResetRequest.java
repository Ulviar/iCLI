package com.github.ulviar.icli.engine.pool.api.hooks;

import com.github.ulviar.icli.engine.InteractiveSession;
import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import java.util.UUID;

/**
 * Describes why the pool is invoking reset hooks for a worker. Reset requests are immutable value objects passed to
 * {@link ResetHook#reset(InteractiveSession, LeaseScope, ResetRequest)} so hooks can tailor their logic to the
 * trigger (normal lease completion, manual reset, or timeout recovery).
 */
public interface ResetRequest {

    /**
     * Returns a request describing a normal lease completion.
     *
     * @param requestId unique identifier of the completed request
     * @return reset request with reason {@link Reason#LEASE_COMPLETED}
     */
    static ResetRequest leaseCompleted(UUID requestId) {
        return new Default(Reason.LEASE_COMPLETED, requestId);
    }

    /**
     * Returns a request describing a manual reset initiated by client code.
     *
     * @param requestId identifier of the active request (borrowed from the lease scope)
     * @return reset request with reason {@link Reason#MANUAL}
     */
    static ResetRequest manual(UUID requestId) {
        return new Default(Reason.MANUAL, requestId);
    }

    /**
     * Returns a request describing a lease that timed out.
     *
     * @param requestId identifier of the timed-out request
     * @return reset request with reason {@link Reason#TIMEOUT}
     */
    static ResetRequest timedOut(UUID requestId) {
        return new Default(Reason.TIMEOUT, requestId);
    }

    /**
     * Reason the reset was triggered.
     *
     * @return reset reason
     */
    Reason reason();

    /**
     * Request identifier associated with the reset.
     *
     * @return request identifier
     */
    UUID requestId();

    enum Reason {
        /**
         * Reset triggered after a lease completed normally.
         */
        LEASE_COMPLETED,

        /**
         * Reset triggered by client code calling {@link
         * com.github.ulviar.icli.engine.pool.api.WorkerLease#reset(ResetRequest)}.
         */
        MANUAL,

        /**
         * Reset triggered after the lease exceeded its timeout.
         */
        TIMEOUT
    }

    record Default(Reason reason, UUID requestId) implements ResetRequest {}
}
