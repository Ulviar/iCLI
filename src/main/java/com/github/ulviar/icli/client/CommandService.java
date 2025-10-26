package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.ProcessEngine;
import com.github.ulviar.icli.core.ProcessResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service wrapper tuned for a single console application. Encapsulates the base command definition, preferred run and
 * session options, and exposes convenience methods for one-off executions or interactive sessions.
 */
public final class CommandService {

    private static final ResponseDecoder DEFAULT_DECODER = new LineDelimitedResponseDecoder();

    private final ProcessEngine engine;
    private final CommandDefinition baseCommand;
    private final ExecutionOptions options;
    private final ClientScheduler scheduler;

    public CommandService(ProcessEngine engine, CommandDefinition baseCommand) {
        this(engine, baseCommand, ExecutionOptions.builder().build());
    }

    public CommandService(ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options) {
        this(engine, baseCommand, options, ClientSchedulers.virtualThreads());
    }

    public CommandService(
            ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options, ClientScheduler scheduler) {
        this.engine = engine;
        this.baseCommand = baseCommand;
        this.options = options;
        this.scheduler = scheduler;
    }

    public CommandResult<String> run() {
        return run(baseCall());
    }

    public CommandResult<String> run(Consumer<CommandCallBuilder> customizer) {
        return run(buildCall(customizer));
    }

    public CommandResult<String> run(CommandCall call) {
        try {
            ProcessResult result = engine.run(call.command(), call.options());
            if (result.exitCode() == 0) {
                return CommandResult.success(result.stdout());
            }
            return CommandResult.failure(
                    new ProcessExecutionException(result.exitCode(), result.stdout(), result.stderr()));
        } catch (RuntimeException ex) {
            return CommandResult.failure(ex);
        }
    }

    /**
     * Run the base command asynchronously, returning a future that can be cancelled to interrupt the underlying
     * process.
     */
    public CompletableFuture<CommandResult<String>> runAsync() {
        return runAsync(baseCall());
    }

    /**
     * Run a customised command asynchronously.
     *
     * @param customizer builder hook for adjusting command arguments and execution options
     */
    public CompletableFuture<CommandResult<String>> runAsync(Consumer<CommandCallBuilder> customizer) {
        return runAsync(buildCall(customizer));
    }

    /**
     * Run the provided {@link CommandCall} asynchronously.
     *
     * @param call fully built command definition and options
     * @return future that resolves to the {@link CommandResult}
     */
    public CompletableFuture<CommandResult<String>> runAsync(CommandCall call) {
        return scheduler.submit(() -> run(call));
    }

    public LineSessionClient openLineSession() {
        return openLineSession(baseCall());
    }

    public LineSessionClient openLineSession(Consumer<CommandCallBuilder> customizer) {
        CommandCall call = buildCall(customizer);
        return openLineSession(call);
    }

    public LineSessionClient openLineSession(CommandCall call) {
        InteractiveSessionClient interactive = openInteractiveSession(call);
        return LineSessionClient.create(interactive, call.decoder(), scheduler);
    }

    public InteractiveSessionClient openInteractiveSession() {
        return openInteractiveSession(baseCall());
    }

    public InteractiveSessionClient openInteractiveSession(Consumer<CommandCallBuilder> customizer) {
        CommandCall call = buildCall(customizer);
        return openInteractiveSession(call);
    }

    public InteractiveSessionClient openInteractiveSession(CommandCall call) {
        return InteractiveSessionClient.wrap(engine.startSession(call.command(), call.options()));
    }

    private CommandCall baseCall() {
        return new CommandCall(baseCommand, options, DEFAULT_DECODER);
    }

    private CommandCall buildCall(Consumer<CommandCallBuilder> customizer) {
        CommandCallBuilder builder = CommandCallBuilder.from(baseCommand, options, DEFAULT_DECODER);
        customizer.accept(builder);
        return builder.build();
    }
}
