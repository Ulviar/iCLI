package com.github.ulviar.icli.client.pooled;

import com.github.ulviar.icli.client.ServiceProcessorListener;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import java.util.function.Consumer;

/**
 * Specification describing how a pooled helper should configure its {@link ProcessPoolConfig} and listener. The spec is
 * intentionally tiny today to keep the public API stable while giving callers a hook for richer configuration and, in
 * the future, Kotlin DSL entry points.
 */
public final class PooledClientSpec {
    private static final Consumer<Builder> DEFAULT = _ -> {};

    private final Consumer<ProcessPoolConfig.Builder> poolConfigurer;
    private final ServiceProcessorListener listener;

    private PooledClientSpec(Consumer<ProcessPoolConfig.Builder> poolConfigurer, ServiceProcessorListener listener) {
        this.poolConfigurer = poolConfigurer;
        this.listener = listener;
    }

    /**
     * Builds a spec by applying the supplied configurator to a fresh builder.
     *
     * @param configurer hook that customises the builder before materialising the spec
     * @return spec capturing the configurator's adjustments
     */
    static PooledClientSpec fromConfigurer(Consumer<PooledClientSpec.Builder> configurer) {
        Builder builder = builder();
        configurer.accept(builder);
        return builder.build();
    }

    /**
     * Returns the pooled configuration hook accumulated by this spec.
     *
     * @return configurator applied to {@link ProcessPoolConfig.Builder} instances
     */
    Consumer<ProcessPoolConfig.Builder> poolConfigurer() {
        return poolConfigurer;
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
        return builder -> builder.pool(config -> config.maxSize(maxSize));
    }

    /**
     * Creates a new spec builder. Callers typically pass the resulting builder to a helper method via lambda:
     *
     * <pre>{@code service.commandRunner(spec -> spec.pool(cfg -> cfg.maxSize(4))); }</pre>
     *
     * @return fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Consumer<ProcessPoolConfig.Builder> poolConfigurer = _ -> {};
        private ServiceProcessorListener listener = ServiceProcessorListener.noOp();

        private Builder() {}

        /**
         * Applies pooling customisation. Multiple invocations are composed in order.
         *
         * @param configurer hook that adjusts the {@link ProcessPoolConfig.Builder}
         * @return this builder
         */
        public Builder pool(Consumer<ProcessPoolConfig.Builder> configurer) {
            poolConfigurer = poolConfigurer.andThen(configurer);
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

        private PooledClientSpec build() {
            return new PooledClientSpec(poolConfigurer, listener);
        }
    }
}
