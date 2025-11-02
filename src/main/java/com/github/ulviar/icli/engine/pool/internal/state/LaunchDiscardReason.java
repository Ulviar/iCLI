package com.github.ulviar.icli.engine.pool.internal.state;

/**
 * Enumerates the reasons a freshly launched worker may be discarded before serving any leases. Discards occur when the
 * pool transitions to closing or terminated while a launch is in flight; the caller receives a {@link
 * com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException} constructed from {@link #message()}.
 */
public enum LaunchDiscardReason {
    POOL_TERMINATED,
    POOL_CLOSING;

    /**
     * Human-readable description used in diagnostics and exceptions when a freshly launched worker cannot enter the
     * pool.
     */
    public String message() {
        return switch (this) {
            case POOL_TERMINATED -> "Process pool has been terminated";
            case POOL_CLOSING -> "Process pool is shutting down";
        };
    }
}
