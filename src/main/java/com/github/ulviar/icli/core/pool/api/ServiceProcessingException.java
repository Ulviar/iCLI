package com.github.ulviar.icli.core.pool.api;

/**
 * Indicates that a worker failed while processing a request. Higher-level APIs translate this into client-facing
 * errors that surface execution diagnostics. The exception is propagated through {@link
 * com.github.ulviar.icli.core.pool.api.ProcessPool} diagnostics before bubbling out to callers.
 */
public final class ServiceProcessingException extends RuntimeException {

    /**
     * Creates an exception with an explanatory message. Prefer messages that reference the affected request identifier
     * when available.
     *
     * @param message description of the processing failure
     */
    public ServiceProcessingException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and root cause.
     *
     * @param message description of the processing failure
     * @param cause underlying exception
     */
    public ServiceProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
