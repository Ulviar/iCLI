package com.github.ulviar.icli.engine.pool.api.hooks;

import com.github.ulviar.icli.engine.pool.internal.lease.DefaultRequestTimeoutScheduler;

/**
 * Factory for creating {@link RequestTimeoutScheduler} instances. A fresh scheduler is constructed for every {@link
 * com.github.ulviar.icli.engine.pool.api.ProcessPool} so the pool controls its lifecycle independently of other pools.
 */
@FunctionalInterface
public interface RequestTimeoutSchedulerFactory {

    /**
     * Creates a new scheduler instance.
     *
     * @return request-timeout scheduler
     */
    RequestTimeoutScheduler create();

    /**
     * Returns the default scheduler factory backed by {@link
     * com.github.ulviar.icli.engine.pool.internal.lease.DefaultRequestTimeoutScheduler}.
     *
     * @return default factory
     */
    static RequestTimeoutSchedulerFactory defaultFactory() {
        return DefaultRequestTimeoutScheduler::new;
    }
}
