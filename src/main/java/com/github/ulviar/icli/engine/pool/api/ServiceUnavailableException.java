package com.github.ulviar.icli.engine.pool.api;

/**
 * Signals that the process pool cannot provide a worker because it is at capacity, shutting down, or the wait timed
 * out. Callers may treat the exception as transient; retries are typically safe once capacity is freed or the pool is
 * recreated.
 */
public final class ServiceUnavailableException extends RuntimeException {

    /**
     * Creates an exception with a human-friendly explanation. Prefer including contextual details such as queue depth
     * or timeout values in {@code message}.
     *
     * @param message description of the failure
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and root cause.
     *
     * @param message description of the failure
     * @param cause underlying exception
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
