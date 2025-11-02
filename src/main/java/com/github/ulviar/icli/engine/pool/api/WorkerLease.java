package com.github.ulviar.icli.engine.pool.api;

import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.InteractiveSession;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;

/**
 * Exclusive handle to a pooled worker. A lease grants temporary ownership of an {@link InteractiveSession} together
 * with the {@link ExecutionOptions} it was launched with and metadata describing the request in flight. Leases are
 * not thread-safe; the caller that acquires the lease is responsible for interacting with the underlying session and
 * closing the lease exactly once.
 *
 * <p>Every method is safe to invoke until {@link #close()} completes. Subsequent invocations of {@code close()} are
 * ignored.
 */
public interface WorkerLease extends AutoCloseable {

    /**
     * Returns the live {@link InteractiveSession} associated with this lease. Callers may interact with the process
     * directly (stdin/stdout/stderr, signals) subject to the configured {@link ExecutionOptions}.
     *
     * @return interactive session backing the lease
     */
    InteractiveSession session();

    /**
     * Returns the immutable {@link ExecutionOptions} used to launch the worker. Exposing the options allows callers to
     * inspect capture limits, idle timeouts, and shutdown policies without reaching back into pool internals.
     *
     * @return execution options applied to the worker
     */
    ExecutionOptions executionOptions();

    /**
     * Provides request-scoped metadata such as the worker identifier, request UUID, and timestamps. The scope assists
     * diagnostics, logging, and custom reset hooks.
     *
     * @return immutable scope describing this lease
     */
    LeaseScope scope();

    /**
     * Invokes the configured reset hooks immediately, allowing callers to perform an additional state cleanse before
     * returning the worker. The pool automatically resets workers when the lease closes; manual resets are useful when
     * running multiple loosely related commands under a single lease.
     *
     * @param request context describing why the reset is triggered
     */
    void reset(ResetRequest request);

    @Override
    void close();
}
