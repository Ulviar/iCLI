package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.client.internal.runner.SessionLauncher;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import java.util.function.Consumer;

/**
 * Runner that opens {@link ListenOnlySessionClient} instances using a shared {@link CommandDefinition} and
 * {@link ExecutionOptions} baseline.
 */
public final class ListenOnlySessionRunner {

    private final SessionLauncher sessionLauncher;
    private final CommandCallFactory callFactory;

    /**
     * Creates a runner rooted in explicit defaults. Intended for collaborators that mirror the factory logic used by
     * {@link CommandService}.
     *
     * @param sessionLauncher launcher responsible for starting interactive sessions
     * @param baseCommand immutable command definition that seeds every call
     * @param options execution defaults shared across listen-only sessions
     * @param defaultDecoder decoder applied to line-oriented helpers when sessions switch out of listen-only mode
     */
    ListenOnlySessionRunner(
            SessionLauncher sessionLauncher,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ResponseDecoder defaultDecoder) {
        this(sessionLauncher, new CommandCallFactory(new RunnerDefaults(baseCommand, options, defaultDecoder)));
    }

    /**
     * Creates a runner backed by a custom command-call factory.
     *
     * @param sessionLauncher launcher responsible for starting interactive sessions
     * @param callFactory factory that produces customised command calls per invocation
     */
    ListenOnlySessionRunner(SessionLauncher sessionLauncher, CommandCallFactory callFactory) {
        this.sessionLauncher = sessionLauncher;
        this.callFactory = callFactory;
    }

    /**
     * Opens a listen-only session using the service defaults.
     *
     * @return listen-only session client bound to a new interactive session
     */
    public ListenOnlySessionClient open() {
        return listenOnly(callFactory.createBaseCall());
    }

    /**
     * Opens a listen-only session after applying the provided customisation.
     *
     * @param customizer consumer that mutates a fresh {@link CommandCallBuilder}
     * @return listen-only session client bound to the customised command
     */
    public ListenOnlySessionClient open(Consumer<CommandCallBuilder> customizer) {
        return listenOnly(callFactory.createCustomCall(customizer));
    }

    /**
     * Opens a listen-only session for the supplied {@link CommandCall}.
     *
     * @param call fully constructed command and execution options
     * @return listen-only session client bound to the specified call
     */
    public ListenOnlySessionClient open(CommandCall call) {
        return listenOnly(call);
    }

    private ListenOnlySessionClient listenOnly(CommandCall call) {
        InteractiveSessionClient session = sessionLauncher.launch(call);
        return ListenOnlySessionClient.wrap(session);
    }
}
