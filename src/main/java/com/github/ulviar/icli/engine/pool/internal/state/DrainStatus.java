package com.github.ulviar.icli.engine.pool.internal.state;

/**
 * Outcome returned by {@link PoolState#drain(long, java.util.List)} summarising whether the drain finished and if the
 * call marked the pool as terminated.
 */
public record DrainStatus(boolean completed, boolean terminatedNow) {}
