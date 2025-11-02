package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;
import java.util.Objects;

/**
 * High-level facade for running a pre-configured command line in one-shot or interactive modes.
 *
 * <p>A {@code CommandService} captures the immutable {@link CommandDefinition} of a console application together with
 * the preferred {@link ExecutionOptions}, a {@link ProcessEngine} implementation, and a {@link ClientScheduler} for
 * asynchronous work. Consumers obtain {@link CommandRunner}, {@link LineSessionRunner}, and
 * {@link InteractiveSessionRunner} instances that honour the same defaults. All helpers respect the defaults declared
 * in the supplied {@link ExecutionOptions}; callers can further customise per-invocation behaviour using
 * {@link CommandCallBuilder}.
 *
 * <p>Instances are thread-safe and designed for reuse. The service does not hold any per-invocation state; each helper
 * constructs a fresh {@link CommandCall} that can be inspected or reused by advanced consumers when necessary.
 *
 * @see CommandDefinition
 * @see ExecutionOptions
 * @see CommandRunner
 * @see LineSessionRunner
 * @see InteractiveSessionRunner
 * @see CommandCallBuilder
 * @see InteractiveSessionClient
 * @see LineSessionClient
 */
public final class CommandService {
    private final CommandRunner runner;
    private final InteractiveSessionRunner interactiveRunner;
    private final LineSessionRunner lineRunner;

    /**
     * Creates a service using default execution options and the virtual-thread scheduler.
     *
     * @param engine process engine responsible for launching commands
     * @param baseCommand immutable command definition to execute for every request
     */
    public CommandService(ProcessEngine engine, CommandDefinition baseCommand) {
        this(engine, baseCommand, ExecutionOptions.builder().build());
    }

    /**
     * Creates a service with caller-specified execution options and the virtual-thread scheduler.
     *
     * @param engine process engine responsible for launching commands
     * @param baseCommand immutable command definition to execute for every request
     * @param options default execution-time configuration applied to every run or session
     */
    public CommandService(ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options) {
        this(engine, baseCommand, options, ClientSchedulers.virtualThreads());
    }

    /**
     * Creates a fully customised service instance.
     *
     * @param engine process engine responsible for launching commands
     * @param baseCommand immutable command definition to execute for every request
     * @param options default execution-time configuration applied to every run or session
     * @param scheduler scheduler used for {@code runAsync} and session helper futures
     */
    public CommandService(
            ProcessEngine engine, CommandDefinition baseCommand, ExecutionOptions options, ClientScheduler scheduler) {
        this(engine, baseCommand, options, scheduler, new LineDelimitedResponseDecoder());
    }

    CommandService(
            ProcessEngine engine,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ClientScheduler scheduler,
            ResponseDecoder defaultDecoder) {
        ProcessEngine ensuredEngine = Objects.requireNonNull(engine, "engine");
        CommandDefinition ensuredCommand = Objects.requireNonNull(baseCommand, "baseCommand");
        ExecutionOptions ensuredOptions = Objects.requireNonNull(options, "options");
        ClientScheduler ensuredScheduler = Objects.requireNonNull(scheduler, "scheduler");
        ResponseDecoder ensuredDecoder = Objects.requireNonNull(defaultDecoder, "defaultDecoder");
        InteractiveSessionStarter sessionStarter = new InteractiveSessionStarter(ensuredEngine);
        this.runner =
                new CommandRunner(ensuredEngine, ensuredCommand, ensuredOptions, ensuredScheduler, ensuredDecoder);
        this.interactiveRunner =
                new InteractiveSessionRunner(sessionStarter, ensuredCommand, ensuredOptions, ensuredDecoder);
        this.lineRunner =
                new LineSessionRunner(sessionStarter, ensuredScheduler, ensuredCommand, ensuredOptions, ensuredDecoder);
    }

    /**
     * Returns the configured command runner for one-shot executions.
     *
     * @return the command runner sharing the service defaults
     */
    public CommandRunner runner() {
        return runner;
    }

    /**
     * Returns a runner that opens interactive sessions using the service defaults.
     *
     * @return interactive session runner
     */
    public InteractiveSessionRunner interactiveSessionRunner() {
        return interactiveRunner;
    }

    /**
     * Returns a runner that opens line-oriented sessions using the service defaults.
     *
     * @return line session runner
     */
    public LineSessionRunner lineSessionRunner() {
        return lineRunner;
    }
}
