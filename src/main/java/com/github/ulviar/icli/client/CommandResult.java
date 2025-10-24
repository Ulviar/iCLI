package com.github.ulviar.icli.client;

import org.jetbrains.annotations.Nullable;

/**
 * Generic container describing the outcome of a high-level client interaction.
 */
public record CommandResult<T>(
        boolean success, @Nullable T value, @Nullable Throwable error) {

    public CommandResult {
        if (success) {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null when success is true");
            }
            if (error != null) {
                throw new IllegalArgumentException("error must be null when success is true");
            }
        } else {
            if (error == null) {
                throw new IllegalArgumentException("error must not be null when success is false");
            }
            if (value != null) {
                throw new IllegalArgumentException("value must be null when success is false");
            }
        }
    }

    public static <T> CommandResult<T> success(T value) {
        return new CommandResult<>(true, value, null);
    }

    public static <T> CommandResult<T> failure(Throwable error) {
        return new CommandResult<>(false, null, error);
    }
}
