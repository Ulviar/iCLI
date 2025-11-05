package com.github.ulviar.icli.fixture;

/**
 * Payload specification for responses.
 */
public record PayloadProfile(PayloadFormat format, int size) {
    public PayloadProfile {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
    }
}
