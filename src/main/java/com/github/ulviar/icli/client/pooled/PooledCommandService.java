package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.ClientScheduler;
import com.github.ulviar.icli.client.CommandService;
import com.github.ulviar.icli.client.ResponseDecoder;
import com.github.ulviar.icli.client.internal.runner.CommandCallFactory;
import com.github.ulviar.icli.client.internal.runner.LineSessionFactory;
import com.github.ulviar.icli.client.internal.runner.RunnerDefaults;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import java.util.function.Consumer;

/**
 * Branch of {@link CommandService} that exposes helpers backed by worker pools.
 *
 * <p>The service itself is lightweight: it does not eagerly allocate pools. Each helper call returns a runner or client
 * that owns its {@link ProcessPoolClient} and must therefore be closed by the caller.</p>
 */
public final class PooledCommandService {

    private final ProcessEngine engine;
    private final CommandDefinition command;
    private final ExecutionOptions options;
    private final ClientScheduler scheduler;
    private final ResponseDecoder defaultDecoder;

    /**
     * Creates a pooled service facade rooted in the supplied defaults. All runners and clients created from this
     * service share the command definition, execution options, scheduler, and default response decoder.
     *
     * @param engine process engine used to launch pooled workers
     * @param command immutable command definition executed by every worker
     * @param options worker defaults applied to every pooled request
     * @param scheduler scheduler used for asynchronous helpers
     * @param defaultDecoder decoder applied to line-oriented helpers unless callers override it on the command call
     */
    public PooledCommandService(
            ProcessEngine engine,
            CommandDefinition command,
            ExecutionOptions options,
            ClientScheduler scheduler,
            ResponseDecoder defaultDecoder) {
        this.engine = engine;
        this.command = command;
        this.options = options;
        this.scheduler = scheduler;
        this.defaultDecoder = defaultDecoder;
    }

    /**
     * Creates a pooled command runner according to the supplied spec configurator.
     *
     * <p>Typical usage borrows the runner in a try-with-resources block so the underlying pool is closed
     * deterministically:</p>
     *
     * <pre>{@code
     * try (PooledCommandRunner runner =
     *         service.commandRunner(spec -> spec.maxSize(4))) {
     *     CommandResult<String> result = runner.process("version");
     *     // inspect result ...
     * }
     * }</pre>
     *
     * @param spec configurator that adjusts pool configuration and diagnostics listener
     * @return command runner that owns its pool
     */
    public PooledCommandRunner commandRunner(Consumer<PooledClientSpec.Builder> spec) {
        PooledClientSpec built = PooledClientSpec.fromConfigurer(command, options, spec);
        ProcessPoolClient client = createClient(built);
        return new PooledCommandRunner(client, defaultDecoder);
    }

    /**
     * Creates a pooled line session runner according to the supplied spec configurator.
     *
     * @param spec configurator that adjusts pool configuration and diagnostics listener
     * @return line session runner that owns its pool
     */
    public PooledLineSessionRunner lineSessionRunner(Consumer<PooledClientSpec.Builder> spec) {
        PooledClientSpec built = PooledClientSpec.fromConfigurer(command, options, spec);
        ProcessPoolClient client = createClient(built);
        return new PooledLineSessionRunner(
                client, createCallFactory(), new LineSessionFactory(scheduler), defaultDecoder);
    }

    /**
     * Creates a pooled interactive session runner according to the supplied spec configurator.
     *
     * @param spec configurator that adjusts pool configuration and diagnostics listener
     * @return interactive session runner that owns its pool
     */
    public PooledInteractiveSessionRunner interactiveSessionRunner(Consumer<PooledClientSpec.Builder> spec) {
        PooledClientSpec built = PooledClientSpec.fromConfigurer(command, options, spec);
        ProcessPoolClient client = createClient(built);
        LineSessionFactory lineSessionFactory = new LineSessionFactory(scheduler);
        return new PooledInteractiveSessionRunner(client, createCallFactory(), lineSessionFactory, defaultDecoder);
    }

    /**
     * Creates a pooled client for advanced scenarios.
     *
     * @param spec configurator that adjusts pool configuration and diagnostics listener
     * @return advanced client that owns its pool
     */
    public ProcessPoolClient client(Consumer<PooledClientSpec.Builder> spec) {
        PooledClientSpec built = PooledClientSpec.fromConfigurer(command, options, spec);
        return createClient(built);
    }

    private ProcessPoolClient createClient(PooledClientSpec spec) {
        ProcessPoolConfig config = spec.poolConfig();
        ServiceProcessorListener listener = spec.listener();
        return ProcessPoolClient.create(engine, config, scheduler, defaultDecoder, listener);
    }

    private CommandCallFactory createCallFactory() {
        RunnerDefaults defaults = new RunnerDefaults(command, options, defaultDecoder);
        return new CommandCallFactory(defaults);
    }
}
