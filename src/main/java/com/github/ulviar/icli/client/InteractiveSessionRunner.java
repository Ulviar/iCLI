package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import java.util.function.Consumer;

/**
 * Reusable launcher for interactive sessions that share a {@link CommandDefinition} and {@link ExecutionOptions}
 * baseline.
 *
 * <p>Instances are created by {@link CommandService} and honour the same defaults. Callers can open sessions using the
 * baseline configuration via {@link #open()}, customise a fresh {@link CommandCallBuilder}, or pass a fully constructed
 * {@link CommandCall}.
 */
public final class InteractiveSessionRunner {

    private final InteractiveSessionStarter starter;
    private final CommandDefinition baseCommand;
    private final ExecutionOptions options;
    private final ResponseDecoder defaultDecoder;

    InteractiveSessionRunner(
            InteractiveSessionStarter starter,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ResponseDecoder defaultDecoder) {
        this.starter = starter;
        this.baseCommand = baseCommand;
        this.options = options;
        this.defaultDecoder = defaultDecoder;
    }

    /**
     * Opens a new session using the service defaults.
     *
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open() {
        return starter.start(createBaseCall());
    }

    /**
     * Opens a new session after customising the base command.
     *
     * @param customizer consumer that mutates a fresh {@link CommandCallBuilder}
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open(Consumer<CommandCallBuilder> customizer) {
        return starter.start(buildCustomCall(customizer));
    }

    /**
     * Opens a session using an explicit {@link CommandCall}.
     *
     * @param call fully constructed command and execution options
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open(CommandCall call) {
        return starter.start(call);
    }

    private CommandCall createBaseCall() {
        return new CommandCall(baseCommand, options, defaultDecoder);
    }

    private CommandCall buildCustomCall(Consumer<CommandCallBuilder> customizer) {
        CommandCallBuilder builder = CommandCallBuilder.from(baseCommand, options, defaultDecoder);
        customizer.accept(builder);
        return builder.build();
    }
}
