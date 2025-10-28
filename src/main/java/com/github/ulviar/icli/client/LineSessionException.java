package com.github.ulviar.icli.client;

/**
 * Indicates that {@link LineSessionClient} was unable to exchange a complete request/response pair.
 *
 * <p>The exception wraps IO failures encountered while writing to stdin or decoding stdout (for example end-of-stream
 * without a terminating newline). The original input line is retained for diagnostics.</p>
 *
 * <h2>Recommended handling</h2>
 * <ul>
 *     <li>consider the underlying interactive session unhealthy; close it and start a fresh session before issuing
 *     further requests;</li>
 *     <li>log the offending input via {@link #input()} alongside {@link #getCause()} to aid debugging;</li>
 *     <li>avoid replaying the same request automatically unless the root cause has been identified.</li>
 * </ul>
 */
public final class LineSessionException extends RuntimeException {

    private final String input;

    LineSessionException(String input, Throwable cause) {
        super("Failed to exchange line with session for input: " + input, cause);
        this.input = input;
    }

    /**
     * Returns the input line that triggered the failure.
     *
     * @return input line supplied to {@link LineSessionClient#process(String)}
     */
    public String input() {
        return input;
    }
}
