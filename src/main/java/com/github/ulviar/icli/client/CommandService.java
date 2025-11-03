package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import java.util.function.Consumer;

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
    private final ProcessEngine engine;
    private final CommandDefinition command;
    private final ExecutionOptions options;
    private final ClientScheduler scheduler;
    private final ResponseDecoder defaultDecoder;
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
        this.engine = engine;
        this.command = baseCommand;
        this.options = options;
        this.scheduler = scheduler;
        this.defaultDecoder = defaultDecoder;
        InteractiveSessionStarter sessionStarter = new InteractiveSessionStarter(this.engine);
        RunnerDefaults runnerDefaults = new RunnerDefaults(this.command, this.options, this.defaultDecoder);
        CommandCallFactory callFactory = new CommandCallFactory(runnerDefaults);
        this.runner = new CommandRunner(this.engine, callFactory, this.scheduler);
        this.interactiveRunner = new InteractiveSessionRunner(sessionStarter, callFactory);
        LineSessionFactory lineSessionFactory = new LineSessionFactory(this.scheduler);
        this.lineRunner = new LineSessionRunner(sessionStarter, lineSessionFactory, callFactory);
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

    /**
     * Creates a pooled client using the service defaults and the {@link ProcessPoolConfig} builder defaults.
     *
     * @return pooled client
     */
    public ProcessPoolClient pooled() {
        return pooled(builder -> {}, ServiceProcessorListener.noOp());
    }

    /**
     * Creates a pooled client with caller-provided configuration customisation.
     *
     * @param configurer hook for adjusting the pool configuration prior to construction
     * @return pooled client
     */
    public ProcessPoolClient pooled(Consumer<ProcessPoolConfig.Builder> configurer) {
        return pooled(configurer, ServiceProcessorListener.noOp());
    }

    /**
     * Creates a pooled client with caller-provided configuration and diagnostics listener.
     *
     * @param configurer hook for adjusting the pool configuration prior to construction
     * @param listener service-level diagnostics listener
     * @return pooled client
     */
    public ProcessPoolClient pooled(Consumer<ProcessPoolConfig.Builder> configurer, ServiceProcessorListener listener) {
        ProcessPoolConfig.Builder builder = ProcessPoolConfig.builder(command);
        builder.workerOptions(options);
        configurer.accept(builder);
        ProcessPoolConfig config = builder.build();
        return ProcessPoolClient.create(engine, config, scheduler, defaultDecoder, listener);
    }
}
