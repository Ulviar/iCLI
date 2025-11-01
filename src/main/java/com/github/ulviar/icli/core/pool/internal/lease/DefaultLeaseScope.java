package com.github.ulviar.icli.core.pool.internal.lease;

import com.github.ulviar.icli.core.pool.api.LeaseScope;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of lease metadata exposed to callers through {@link LeaseScope}. Each scope captures the worker
 * identifier, lifecycle timestamps, reuse count, and a fresh request id so diagnostics and timeout enforcement can
 * safely reference them outside the pool lock.
 *
 * <p>Instances are thread-safe and never mutate after construction.
 */
public final class DefaultLeaseScope implements LeaseScope {

    private final UUID requestId;
    private final int workerId;
    private final Instant leaseStart;
    private final Instant workerCreatedAt;
    private final long reuseCount;

    /**
     * Takes an instantaneous snapshot of {@code worker} state when the lease begins.
     *
     * @param worker worker being leased
     * @param leaseStart timestamp recorded by the pool clock when the lease started
     */
    public DefaultLeaseScope(PoolWorker worker, Instant leaseStart) {
        this.requestId = UUID.randomUUID();
        this.workerId = worker.id();
        this.leaseStart = leaseStart;
        this.workerCreatedAt = worker.createdAt();
        this.reuseCount = worker.reuseCount();
    }

    @Override
    public UUID requestId() {
        return requestId;
    }

    @Override
    public int workerId() {
        return workerId;
    }

    @Override
    public Instant leaseStart() {
        return leaseStart;
    }

    @Override
    public Instant workerCreatedAt() {
        return workerCreatedAt;
    }

    @Override
    public long reuseCount() {
        return reuseCount;
    }
}
