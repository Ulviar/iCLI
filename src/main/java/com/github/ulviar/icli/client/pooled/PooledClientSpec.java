package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import com.github.ulviar.icli.engine.pool.api.hooks.RequestTimeoutSchedulerFactory;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetHook;
import com.github.ulviar.icli.engine.pool.api.hooks.WarmupAction;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Specification describing how a pooled helper should configure its {@link ProcessPoolConfig} and listener. The spec is
 * intentionally tiny today to keep the public API stable while giving callers a hook for richer configuration and, in
 * the future, Kotlin DSL entry points.
 */
public final class PooledClientSpec {
    private static final Consumer<Builder> DEFAULT = _ -> {};

    private final ProcessPoolConfig poolConfig;
    private final ServiceProcessorListener listener;

    private PooledClientSpec(ProcessPoolConfig poolConfig, ServiceProcessorListener listener) {
        this.poolConfig = poolConfig;
        this.listener = listener;
    }

    /**
     * Builds a spec by applying the supplied configurator to a fresh builder.
     *
     * @param configurer hook that customises the builder before materialising the spec
     * @return spec capturing the configurator's adjustments
     */
    static PooledClientSpec fromConfigurer(
            CommandDefinition command, ExecutionOptions options, Consumer<PooledClientSpec.Builder> configurer) {
        Builder builder = builder(command, options);
        configurer.accept(builder);
        return builder.build();
    }

    /**
     * Returns the pooled configuration captured by this spec.
     *
     * @return immutable {@link ProcessPoolConfig}
     */
    ProcessPoolConfig poolConfig() {
        return poolConfig;
    }

    /**
     * Returns the listener associated with this spec.
     *
     * @return diagnostics listener invoked for pooled service events
     */
    ServiceProcessorListener listener() {
        return listener;
    }

    /**
     * Returns a configurator that keeps all defaults untouched.
     *
     * @return configurator that applies no customisation
     */
    public static Consumer<Builder> defaultSpec() {
        return DEFAULT;
    }

    /**
     * Returns a configurator that sets the pool's maximum size while leaving all other defaults intact.
     *
     * @param maxSize desired maximum number of workers
     * @return configurator that enforces the provided maximum size
     */
    public static Consumer<Builder> withMaxSize(int maxSize) {
        return builder -> builder.maxSize(maxSize);
    }

    /**
     * Creates a new spec builder. Callers typically pass the resulting builder to a helper method via lambda:
     *
     * <pre>{@code service.commandRunner(spec -> spec.maxSize(4)); }</pre>
     *
     * @return fresh builder
     */
    public static Builder builder(CommandDefinition command, ExecutionOptions options) {
        ProcessPoolConfig.Builder poolBuilder = ProcessPoolConfig.builder(command);
        poolBuilder.workerOptions(options);
        return new Builder(poolBuilder);
    }

    public static final class Builder {
        private final ProcessPoolConfig.Builder poolBuilder;
        private ServiceProcessorListener listener = ServiceProcessorListener.noOp();

        private Builder(ProcessPoolConfig.Builder poolBuilder) {
            this.poolBuilder = poolBuilder;
        }

        /**
         * Applies pooling customisation. Multiple invocations are composed in order.
         *
         * @param configurer hook that adjusts the {@link ProcessPoolConfig.Builder}
         * @return this builder
         */
        public Builder pool(Consumer<ProcessPoolConfig.Builder> configurer) {
            configurer.accept(poolBuilder);
            return this;
        }

        /**
         * Overrides the listener used for pooled request/response events.
         *
         * @param listener diagnostics listener
         * @return this builder
         */
        public Builder listener(ServiceProcessorListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder workerOptions(ExecutionOptions options) {
            poolBuilder.workerOptions(options);
            return this;
        }

        public Builder minSize(int value) {
            poolBuilder.minSize(value);
            return this;
        }

        public Builder maxSize(int value) {
            poolBuilder.maxSize(value);
            return this;
        }

        public Builder maxQueueDepth(int value) {
            poolBuilder.maxQueueDepth(value);
            return this;
        }

        public Builder maxRequestsPerWorker(int value) {
            poolBuilder.maxRequestsPerWorker(value);
            return this;
        }

        public Builder maxWorkerLifetime(Duration value) {
            poolBuilder.maxWorkerLifetime(value);
            return this;
        }

        public Builder maxIdleTime(Duration value) {
            poolBuilder.maxIdleTime(value);
            return this;
        }

        public Builder leaseTimeout(Duration value) {
            poolBuilder.leaseTimeout(value);
            return this;
        }

        public Builder requestTimeout(Duration value) {
            poolBuilder.requestTimeout(value);
            return this;
        }

        public Builder destroyProcessTree(boolean value) {
            poolBuilder.destroyProcessTree(value);
            return this;
        }

        public Builder warmupAction(@Nullable WarmupAction action) {
            poolBuilder.warmupAction(action);
            return this;
        }

        public Builder addResetHook(ResetHook hook) {
            poolBuilder.addResetHook(hook);
            return this;
        }

        public Builder diagnosticsListener(PoolDiagnosticsListener listener) {
            poolBuilder.diagnosticsListener(listener);
            return this;
        }

        public Builder clock(Clock value) {
            poolBuilder.clock(value);
            return this;
        }

        public Builder requestTimeoutSchedulerFactory(RequestTimeoutSchedulerFactory factory) {
            poolBuilder.requestTimeoutSchedulerFactory(factory);
            return this;
        }

        public Builder invariantChecksEnabled(boolean value) {
            poolBuilder.invariantChecksEnabled(value);
            return this;
        }

        private PooledClientSpec build() {
            ProcessPoolConfig config = poolBuilder.build();
            return new PooledClientSpec(config, listener);
        }
    }
}
