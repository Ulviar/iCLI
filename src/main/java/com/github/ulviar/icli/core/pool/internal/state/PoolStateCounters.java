package com.github.ulviar.icli.core.pool.internal.state;

/**
 * Internal counter snapshot exposed for testing. The record resides in the {@code internal.state} package so Kotlin
 * tests can validate invariants even when JVM assertions are disabled, without widening the production API. Values
 * correspond to {@link CapacityLedger} and {@link com.github.ulviar.icli.core.pool.internal.concurrent.LifecycleGate}
 * state at the moment the snapshot was captured.
 */
record PoolStateCounters(
        int allocated, int idle, int active, int launching, int waiters, boolean closing, boolean terminated) {}
