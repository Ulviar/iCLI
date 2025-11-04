package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.client.pooled.PooledCommandService;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;

/**
 * High-level facade for running a pre-configured command line in one-shot or interactive modes.
 *
 * <p>A {@code CommandService} captures the immutable {@link CommandDefinition} of a console application together with
 * the preferred {@link ExecutionOptions}, a {@link ProcessEngine} implementation, and a {@link ClientScheduler} for
 * asynchronous work. Consumers obtain {@link CommandRunner}, {@link LineSessionRunner}, {@link ListenOnlySessionRunner},
 * and {@link InteractiveSessionRunner} instances that honour the same defaults. All helpers respect the defaults
 * declared in the supplied {@link ExecutionOptions}; callers can further customise per-invocation behaviour using
 * {@link CommandCallBuilder}.
 *
 * <p>Instances are thread-safe and designed for reuse. The service does not hold any per-invocation state; each helper
 * constructs a fresh {@link CommandCall} that can be inspected or reused by advanced consumers when necessary.
 *
 * <p>Worker pooling is exposed via {@link #pooled()}. Essential API callers can rely on the returned
 * {@link PooledCommandService} for command runners and conversations, while advanced scenarios may obtain a direct
 * {@link com.github.ulviar.icli.client.pooled.ProcessPoolClient} through
 * {@link PooledCommandService#client(java.util.function.Consumer)} when they need to manage pools manually.</p>
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
    private final ListenOnlySessionRunner listenOnlyRunner;
    private final LineSessionRunner lineRunner;
    private final PooledCommandService pooledCommandService;

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

    /**
     * Creates a service with explicit defaults, intended for collaborators that need to override the decoder used for
     * line-oriented helpers.
     *
     * @param engine process engine responsible for launching commands
     * @param baseCommand immutable command definition to execute for every request
     * @param options default execution-time configuration applied to every run or session
     * @param scheduler scheduler used for {@code runAsync} and session helper futures
     * @param defaultDecoder decoder applied to line-oriented helpers unless callers override it on the command call
     */
    private CommandService(
            ProcessEngine engine,
            CommandDefinition baseCommand,
            ExecutionOptions options,
            ClientScheduler scheduler,
            ResponseDecoder defaultDecoder) {
        InteractiveSessionStarter sessionStarter = new InteractiveSessionStarter(engine);
        RunnerDefaults runnerDefaults = new RunnerDefaults(baseCommand, options, defaultDecoder);
        CommandCallFactory callFactory = new CommandCallFactory(runnerDefaults);
        this.runner = new CommandRunner(engine, callFactory, scheduler);
        this.interactiveRunner = new InteractiveSessionRunner(sessionStarter, callFactory);
        LineSessionFactory lineSessionFactory = new LineSessionFactory(scheduler);
        this.lineRunner = new LineSessionRunner(sessionStarter, lineSessionFactory, callFactory);
        this.listenOnlyRunner = new ListenOnlySessionRunner(sessionStarter, callFactory);
        this.pooledCommandService = new PooledCommandService(engine, baseCommand, options, scheduler, defaultDecoder);
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
     * Returns a runner that opens listen-only sessions rooted in the service defaults.
     *
     * @return listen-only session runner
     */
    public ListenOnlySessionRunner listenOnlyRunner() {
        return listenOnlyRunner;
    }

    /**
     * Returns the branch for configuring pooled execution helpers.
     *
     * <p>The returned facade does not create pools eagerly; each helper allocates its own
     * {@link com.github.ulviar.icli.client.pooled.ProcessPoolClient} so lifecycles remain scoped to the runner or
     * conversation returned by that helper. Call {@link PooledCommandService#client(java.util.function.Consumer)} when
     * you need the advanced {@code ProcessPoolClient} API directly.</p>
     *
     * @return pooled command service rooted in this facade’s defaults
     */
    public PooledCommandService pooled() {
        return pooledCommandService;
    }
}
