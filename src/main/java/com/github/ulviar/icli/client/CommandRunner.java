package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.ProcessEngine;
import com.github.ulviar.icli.core.ProcessResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Executes a preconfigured console command using the provided {@link ProcessEngine}.
 *
 * <p>A {@code CommandRunner} encapsulates the immutable {@link CommandDefinition}, default {@link ExecutionOptions},
 * response decoding strategy, and {@link ClientScheduler} needed to run the command in synchronous or asynchronous
 * modes. Callers can reuse a runner across multiple invocations and customise each call via the supplied
 * {@link CommandCallBuilder}. All helper methods construct fresh {@link CommandCall} instances so the runner itself
 * remains stateless and thread-safe.
 *
 * <p>Library users obtain runners via {@link CommandService#runner()}; restricting construction to package
 * collaborators ensures each runner honours the service-level defaults.
 */
public final class CommandRunner {

    private final ProcessEngine engine;
    private final CommandDefinition baseCommand;
    private final ExecutionOptions options;
    private final ClientScheduler scheduler;
    private final ResponseDecoder defaultDecoder;

    /**
     * Creates a runner with default execution options and the virtual-thread scheduler.
     *
     * @param engine      process engine responsible for launching commands
     * @param baseCommand immutable command definition used for every invocation
     */
    CommandRunner(ProcessEngine engine, CommandDefinition baseCommand) {
        this(engine, baseCommand, ExecutionOptions.builder().build());
    }

    /**
     * Creates a runner with custom execution options and the virtual-thread scheduler.
     *
     * @param engine      process engine responsible for launching commands
     * @param baseCommand immutable command definition used for every invocation
     * @param options     execution-time configuration applied to each run unless overridden per-call
     */
    CommandRunner(ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options) {
        this(engine, baseCommand, options, ClientSchedulers.virtualThreads());
    }

    /**
     * Creates a runner with fully customised defaults.
     *
     * @param engine      process engine responsible for launching commands
     * @param baseCommand immutable command definition used for every invocation
     * @param options     execution-time configuration applied to each run unless overridden per-call
     * @param scheduler   scheduler used for asynchronous helpers such as {@link #runAsync()}
     */
    CommandRunner(
            ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options, ClientScheduler scheduler) {
        this(engine, baseCommand, options, scheduler, new LineDelimitedResponseDecoder());
    }

    CommandRunner(
            ProcessEngine engine,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ClientScheduler scheduler,
            ResponseDecoder defaultDecoder) {
        this.engine = engine;
        this.baseCommand = baseCommand;
        this.options = options;
        this.scheduler = scheduler;
        this.defaultDecoder = defaultDecoder;
    }

    /**
     * Runs the base command with the runner defaults applied.
     *
     * @return {@link CommandResult#success(Object)} when the command exits with code {@code 0}; otherwise a failure
     * containing process diagnostics or the thrown exception
     */
    public CommandResult<String> run() {
        return run(createBaseCall());
    }

    /**
     * Runs the command after applying the provided customisation.
     *
     * @param customizer consumer that configures a fresh {@link CommandCallBuilder}
     *
     * @return the completed command result
     */
    public CommandResult<String> run(Consumer<CommandCallBuilder> customizer) {
        return run(buildCustomCall(customizer));
    }

    /**
     * Runs an explicit command call.
     *
     * @param call fully constructed command and execution options
     *
     * @return the completed command result. Non-zero exits produce {@link ProcessExecutionException}; unexpected runtime
     * failures are wrapped in {@link CommandRunnerException}.
     */
    public CommandResult<String> run(CommandCall call) {
        try {
            ProcessResult result = engine.run(call.command(), call.options());
            if (result.exitCode() == 0) {
                return CommandResult.success(result.stdout());
            }
            return CommandResult.failure(
                    new ProcessExecutionException(result.exitCode(), result.stdout(), result.stderr()));
        } catch (RuntimeException ex) {
            return CommandResult.failure(new CommandRunnerException(call, ex));
        }
    }

    /**
     * Runs the base command asynchronously.
     *
     * @return a future that yields the {@link CommandResult}; cancellation interrupts the underlying process according
     * to {@link ExecutionOptions#shutdownPlan()}
     */
    public CompletableFuture<CommandResult<String>> runAsync() {
        return runAsync(createBaseCall());
    }

    /**
     * Runs a customised command asynchronously.
     *
     * @param customizer consumer that configures a fresh {@link CommandCallBuilder}
     *
     * @return future that yields the {@link CommandResult}; cancellation interrupts the underlying process according
     * to {@link ExecutionOptions#shutdownPlan()}
     */
    public CompletableFuture<CommandResult<String>> runAsync(Consumer<CommandCallBuilder> customizer) {
        return runAsync(buildCustomCall(customizer));
    }

    /**
     * Runs the provided command call asynchronously.
     *
     * @param call fully constructed command and execution options
     *
     * @return future that yields the {@link CommandResult}; cancellation interrupts the underlying process according
     * to {@link ExecutionOptions#shutdownPlan()}
     */
    public CompletableFuture<CommandResult<String>> runAsync(CommandCall call) {
        return scheduler.submit(() -> run(call));
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
