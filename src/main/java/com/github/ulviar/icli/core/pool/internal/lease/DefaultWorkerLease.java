package com.github.ulviar.icli.core.pool.internal.lease;

import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.InteractiveSession;
import com.github.ulviar.icli.core.pool.api.LeaseScope;
import com.github.ulviar.icli.core.pool.api.WorkerLease;
import com.github.ulviar.icli.core.pool.api.hooks.ResetRequest;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link WorkerLease} implementation used by the pool runtime. The lease owns the relationship between the
 * borrower and the underlying {@link PoolWorker} until {@link #close()} is invoked. All heavy coordination is handed
 * off to {@link LeaseCallbacks} so the lease can remain a lightweight façade that exposes the {@link
 * com.github.ulviar.icli.core.InteractiveSession}, {@link ExecutionOptions}, and {@link LeaseScope}.
 *
 * <p>Thread-safety: the type is safe for concurrent calls to {@link #reset(ResetRequest)} and {@link #close()}.
 * Closing is idempotent and guarantees the release callback runs at most once.
 */
public final class DefaultWorkerLease implements WorkerLease {

    private final LeaseCallbacks callbacks;
    private final PoolWorker worker;
    private final DefaultLeaseScope scope;
    private final UUID requestId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a lease bound to {@code worker} and registers it with the {@link ActiveLeaseRegistry} via the supplied
     * callbacks. Callers should retain the returned {@link LeaseScope} when publishing diagnostics or enforcing
     * request-level deadlines; the lease reuses the same scope across subsequent callbacks.
     *
     * @param callbacks coordination hooks owned by the pool runtime
     * @param worker worker associated with the lease
     * @param scope immutable scope snapshot captured at the start of the lease
     */
    public DefaultWorkerLease(LeaseCallbacks callbacks, PoolWorker worker, DefaultLeaseScope scope) {
        this.callbacks = callbacks;
        this.worker = worker;
        this.scope = scope;
        this.requestId = scope.requestId();
        this.callbacks.registerActiveLease(worker.id(), scope);
    }

    @Override
    public InteractiveSession session() {
        return worker.session();
    }

    @Override
    public ExecutionOptions executionOptions() {
        return worker.options();
    }

    @Override
    public LeaseScope scope() {
        return scope;
    }

    /**
     * Requests a worker reset while the lease is still active. Invocations after {@link #close()} complete immediately
     * without reaching the callbacks. Reset hooks may retire the worker; callers should be prepared for subsequent
     * acquisitions to receive a different worker id.
     *
     * @param request reason provided to reset hooks
     */
    @Override
    public void reset(ResetRequest request) {
        if (closed.get()) {
            return;
        }
        callbacks.resetLease(worker, scope, request);
    }

    /**
     * Releases the leased worker back to the pool. Only the first invocation triggers {@link
     * LeaseCallbacks#releaseLease}; subsequent calls are ignored so callers can safely close leases from {@code
     * finally} blocks or idempotent cleanup routines.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        callbacks.releaseLease(worker, scope, requestId);
    }
}
