package com.github.ulviar.icli.client.internal.runner;

import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.CommandCallBuilder;
import com.github.ulviar.icli.client.ResponseDecoder;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import java.util.function.Consumer;

/**
 * Immutable bundle of shared runner configuration: the base command definition, execution options, and default
 * response decoder.
 */
public record RunnerDefaults(CommandDefinition command, ExecutionOptions options, ResponseDecoder responseDecoder) {

    /**
     * Returns the base {@link CommandCall} that mirrors the original service defaults.
     *
     * @return immutable command call
     */
    public CommandCall baseCall() {
        return new CommandCall(command, options, responseDecoder);
    }

    /**
     * Builds a new {@link CommandCall} after applying the provided customisation to a fresh {@link CommandCallBuilder}.
     *
     * @param customizer callback used to mutate a builder initialised with the defaults
     * @return immutable command call reflecting the customisation
     */
    public CommandCall buildCall(Consumer<CommandCallBuilder> customizer) {
        CommandCallBuilder builder = newBuilder();
        customizer.accept(builder);
        return builder.build();
    }

    /**
     * Creates a builder pre-populated with the default command, options, and decoder.
     *
     * @return new builder instance
     */
    public CommandCallBuilder newBuilder() {
        return CommandCallBuilder.from(command, options, responseDecoder);
    }
}
