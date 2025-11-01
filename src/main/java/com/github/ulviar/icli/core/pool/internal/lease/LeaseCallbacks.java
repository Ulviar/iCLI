package com.github.ulviar.icli.core.pool.internal.lease;

import com.github.ulviar.icli.core.pool.api.hooks.ResetRequest;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.util.UUID;

/**
 * Callbacks supplied to {@link DefaultWorkerLease} so the outer pool can observe lifecycle events without exposing its
 * coordination primitives. Implementations must be thread-safe; the lease may invoke these methods concurrently from
 * arbitrary caller threads.
 */
public interface LeaseCallbacks {

    /**
     * Registers an active lease under the provided worker identifier. The implementation typically stores the scope in
     * an {@link ActiveLeaseRegistry} so timeout logic can locate request metadata.
     *
     * @param workerId identifier of the worker that was leased
     * @param scope immutable scope describing the active lease
     */
    void registerActiveLease(int workerId, DefaultLeaseScope scope);

    /**
     * Initiates a worker reset while the lease remains active. Implementations should honour the supplied
     * {@link ResetRequest} reason when reporting diagnostics.
     *
     * @param worker worker being reset
     * @param scope immutable scope captured at lease start
     * @param request reset context
     */
    void resetLease(PoolWorker worker, DefaultLeaseScope scope, ResetRequest request);

    /**
     * Releases the worker back to the pool once the lease has closed.
     *
     * @param worker worker being released
     * @param scope immutable scope captured at lease start
     * @param requestId request identifier used for diagnostics
     */
    void releaseLease(PoolWorker worker, DefaultLeaseScope scope, UUID requestId);
}
