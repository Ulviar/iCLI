package com.github.ulviar.icli.client;

/**
 * Signals that an expect-style scripted interaction failed.
 *
 * <p>The helper emits this exception whenever it cannot deliver the next response line: for example, when stdout closes
 * unexpectedly, a decoded value does not match the expectation, or a timeout elapses. Consumers should inspect the
 * message and optional cause to diagnose the failing step.</p>
 */
public class LineExpectationException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message summary describing why the expectation failed
     */
    LineExpectationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying failure cause.
     *
     * @param message summary describing why the expectation failed
     * @param cause root exception that triggered the failure
     */
    LineExpectationException(String message, Throwable cause) {
        super(message, cause);
    }
}
