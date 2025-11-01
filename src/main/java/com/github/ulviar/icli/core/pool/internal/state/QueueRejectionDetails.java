package com.github.ulviar.icli.core.pool.internal.state;

/**
 * Structured diagnostic describing why a request was rejected by the waiter queue.
 */
public record QueueRejectionDetails(int pendingWaiters, int capacity) {}
