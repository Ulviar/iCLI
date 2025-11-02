package com.github.ulviar.icli.engine.pool.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata associated with a leased worker. The scope is immutable and survives for the lifetime of a {@link
 * com.github.ulviar.icli.engine.pool.api.WorkerLease}. It allows diagnostics, logging, and reset hooks to correlate pool
 * activity with client-visible requests.
 */
public interface LeaseScope {

    /**
     * Globally unique identifier assigned to the request when the worker was leased. The identifier remains stable for
     * the entire lease duration and may be used to correlate diagnostics entries with client operations.
     *
     * @return immutable request identifier
     */
    UUID requestId();

    /**
     * Stable numeric identifier for the worker process. Worker identifiers increment monotonically as the pool creates
     * new processes.
     *
     * @return worker identifier
     */
    int workerId();

    /**
     * Timestamp indicating when the lease was granted. Values are expressed using the pool’s {@link
     * ProcessPoolConfig#clock()}.
     *
     * @return lease start instant
     */
    Instant leaseStart();

    /**
     * Timestamp indicating when the worker was originally launched. The instant reflects the same clock used by {@link
     * #leaseStart()}.
     *
     * @return worker creation instant
     */
    Instant workerCreatedAt();

    /**
     * Number of requests that have already completed on this worker before the current lease. The count increments
     * after each successful reset.
     *
     * @return non-negative reuse counter
     */
    long reuseCount();
}
