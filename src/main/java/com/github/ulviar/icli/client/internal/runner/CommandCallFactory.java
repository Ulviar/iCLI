package com.github.ulviar.icli.client.internal.runner;

import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.CommandCallBuilder;
import java.util.function.Consumer;

/**
 * Factory for constructing {@link CommandCall} instances derived from shared runner defaults.
 */
public final class CommandCallFactory {

    private final RunnerDefaults defaults;

    /**
     * Creates a factory bound to immutable runner defaults.
     *
     * @param defaults shared configuration containing the base command, execution options, and response decoder
     */
    public CommandCallFactory(RunnerDefaults defaults) {
        this.defaults = defaults;
    }

    /**
     * Returns a {@link CommandCall} representing the service defaults.
     *
     * @return immutable command call
     */
    public CommandCall createBaseCall() {
        return defaults.baseCall();
    }

    /**
     * Creates a {@link CommandCall} by applying the supplied customiser to a fresh builder seeded with the defaults.
     *
     * @param customiser callback that mutates the builder
     * @return immutable command call reflecting the customisation
     */
    public CommandCall createCustomCall(Consumer<CommandCallBuilder> customiser) {
        return defaults.buildCall(customiser);
    }

    /**
     * Creates a new {@link CommandCallBuilder} seeded with the defaults so advanced code can compose calls manually.
     *
     * @return builder instance
     */
    public CommandCallBuilder newBuilder() {
        return defaults.newBuilder();
    }
}
