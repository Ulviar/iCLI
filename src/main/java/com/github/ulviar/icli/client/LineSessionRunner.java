package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import java.util.function.Consumer;

/**
 * Runner that opens {@link LineSessionClient} instances reusing a shared command definition and execution options.
 *
 * <p>Created via {@link CommandService#lineSessionRunner()}, the runner applies the service defaults and lets callers
 * customise each session through the familiar {@link CommandCallBuilder} DSL.
 */
public final class LineSessionRunner {

    private final InteractiveSessionStarter starter;
    private final ClientScheduler scheduler;
    private final CommandDefinition baseCommand;
    private final ExecutionOptions options;
    private final ResponseDecoder defaultDecoder;

    LineSessionRunner(
            InteractiveSessionStarter starter,
            ClientScheduler scheduler,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ResponseDecoder defaultDecoder) {
        this.starter = starter;
        this.scheduler = scheduler;
        this.baseCommand = baseCommand;
        this.options = options;
        this.defaultDecoder = defaultDecoder;
    }

    /**
     * Opens a line-oriented session using the service defaults.
     *
     * @return {@link LineSessionClient} configured with the default decoder and scheduler
     */
    public LineSessionClient open() {
        return createLineSession(createBaseCall());
    }

    /**
     * Opens a line-oriented session after applying customisation.
     *
     * @param customizer consumer that mutates a fresh {@link CommandCallBuilder}
     * @return {@link LineSessionClient} configured with the resolved decoder and scheduler
     */
    public LineSessionClient open(Consumer<CommandCallBuilder> customizer) {
        return createLineSession(buildCustomCall(customizer));
    }

    /**
     * Opens a line-oriented session for an explicit {@link CommandCall}.
     *
     * @param call fully constructed command and execution options
     * @return {@link LineSessionClient} configured with the resolved decoder and scheduler
     */
    public LineSessionClient open(CommandCall call) {
        return createLineSession(call);
    }

    private LineSessionClient createLineSession(CommandCall call) {
        InteractiveSessionClient session = starter.start(call);
        return LineSessionClient.create(session, call.decoder(), scheduler);
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
