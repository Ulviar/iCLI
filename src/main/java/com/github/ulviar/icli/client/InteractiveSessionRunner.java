package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.client.internal.runner.SessionLauncher;
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

    private final SessionLauncher sessionLauncher;
    private final CommandCallFactory callFactory;

    InteractiveSessionRunner(
            SessionLauncher sessionLauncher,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ResponseDecoder defaultDecoder) {
        this(sessionLauncher, new CommandCallFactory(new RunnerDefaults(baseCommand, options, defaultDecoder)));
    }

    InteractiveSessionRunner(SessionLauncher sessionLauncher, CommandCallFactory callFactory) {
        this.sessionLauncher = sessionLauncher;
        this.callFactory = callFactory;
    }

    /**
     * Opens a new session using the service defaults.
     *
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open() {
        return sessionLauncher.launch(callFactory.createBaseCall());
    }

    /**
     * Opens a new session after customising the base command.
     *
     * @param customizer consumer that mutates a fresh {@link CommandCallBuilder}
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open(Consumer<CommandCallBuilder> customizer) {
        return sessionLauncher.launch(callFactory.createCustomCall(customizer));
    }

    /**
     * Opens a session using an explicit {@link CommandCall}.
     *
     * @param call fully constructed command and execution options
     * @return interactive session client wrapping the launched process
     */
    public InteractiveSessionClient open(CommandCall call) {
        return sessionLauncher.launch(call);
    }
}
