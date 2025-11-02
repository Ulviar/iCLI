package com.github.ulviar.icli.client;

import java.time.Duration;
import java.util.Locale;

/**
 * Indicates that the helper waited for output but the process did not respond before the timeout elapsed. The message
 * reports the elapsed time in milliseconds and names the expectation that could not be satisfied.
 */
public final class LineExpectationTimeoutException extends LineExpectationException {

    /**
     * Creates a timeout exception.
     *
     * @param timeout maximum wait duration that elapsed
     * @param expectation description of the awaited condition (for example, {@code "next line"})
     */
    LineExpectationTimeoutException(Duration timeout, String expectation) {
        super(String.format(
                Locale.ROOT, "Timed out after %d ms while waiting for %s", timeout.toMillis(), expectation));
    }
}
