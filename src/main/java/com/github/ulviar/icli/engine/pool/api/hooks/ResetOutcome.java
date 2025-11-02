package com.github.ulviar.icli.engine.pool.api.hooks;

/**
 * Result produced by a {@link ResetHook}. Returning {@link #RETIRE} instructs the pool to dispose of the worker rather
 * than returning it to the idle queue.
 */
public enum ResetOutcome {
    /**
     * The worker is healthy and may be returned to the pool.
     */
    CONTINUE,

    /**
     * The worker should be retired immediately.
     */
    RETIRE
}
