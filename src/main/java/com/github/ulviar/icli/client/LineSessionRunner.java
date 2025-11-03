package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.client.internal.runner.SessionLauncher;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import java.util.function.Consumer;

/**
 * Runner that opens {@link LineSessionClient} instances reusing a shared command definition and execution options.
 *
 * <p>Created via {@link CommandService#lineSessionRunner()}, the runner applies the service defaults and lets callers
 * customise each session through the familiar {@link CommandCallBuilder} DSL.
 */
public final class LineSessionRunner {

    private final SessionLauncher sessionLauncher;
    private final LineSessionFactory lineSessionFactory;
    private final CommandCallFactory callFactory;

    LineSessionRunner(
            SessionLauncher sessionLauncher,
            ClientScheduler scheduler,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ResponseDecoder defaultDecoder) {
        this(
                sessionLauncher,
                new LineSessionFactory(scheduler),
                new CommandCallFactory(new RunnerDefaults(baseCommand, options, defaultDecoder)));
    }

    LineSessionRunner(
            SessionLauncher sessionLauncher, LineSessionFactory lineSessionFactory, CommandCallFactory callFactory) {
        this.sessionLauncher = sessionLauncher;
        this.lineSessionFactory = lineSessionFactory;
        this.callFactory = callFactory;
    }

    /**
     * Opens a line-oriented session using the service defaults.
     *
     * @return {@link LineSessionClient} configured with the default decoder and scheduler
     */
    public LineSessionClient open() {
        return lineSessionFactory.open(sessionLauncher, callFactory.createBaseCall());
    }

    /**
     * Opens a line-oriented session after applying customisation.
     *
     * @param customizer consumer that mutates a fresh {@link CommandCallBuilder}
     * @return {@link LineSessionClient} configured with the resolved decoder and scheduler
     */
    public LineSessionClient open(Consumer<CommandCallBuilder> customizer) {
        return lineSessionFactory.open(sessionLauncher, callFactory.createCustomCall(customizer));
    }

    /**
     * Opens a line-oriented session for an explicit {@link CommandCall}.
     *
     * @param call fully constructed command and execution options
     * @return {@link LineSessionClient} configured with the resolved decoder and scheduler
     */
    public LineSessionClient open(CommandCall call) {
        return lineSessionFactory.open(sessionLauncher, call);
    }
}
