package com.github.ulviar.icli.core.pool.api;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.pool.api.hooks.RequestTimeoutSchedulerFactory;
import com.github.ulviar.icli.core.pool.api.hooks.ResetHook;
import com.github.ulviar.icli.core.pool.api.hooks.WarmupAction;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable configuration applied when constructing a {@link ProcessPool}. The config captures sizing limits, timeout
 * policies, warm-up behaviour, diagnostics integration, and hook execution for pooled workers. Instances are created
 * via the {@link Builder} and may be safely shared across threads.
 *
 * <p>The builder applies opinionated defaults suitable for most automation workloads:
 * <ul>
 *     <li>{@linkplain Builder#maxSize(int) Pool size} scales with available processors (capped at eight) unless
 *         overridden.</li>
 *     <li>{@linkplain Builder#leaseTimeout(Duration) Lease waits} default to 30 seconds, while {@linkplain
 *         Builder#requestTimeout(Duration) request execution} defaults to five minutes.</li>
 *     <li>Workers are recycled after 30 minutes of lifetime, 5 minutes of idleness, or 1&nbsp;000 requests.</li>
 * </ul>
 *
 * <p>All duration-based thresholds accept {@code Duration.ZERO} to disable the corresponding policy.
 */
public final class ProcessPoolConfig {

    private final CommandDefinition workerCommand;
    private final ExecutionOptions workerOptions;
    private final int minSize;
    private final int maxSize;
    private final int maxQueueDepth;
    private final int maxRequestsPerWorker;
    private final Duration maxWorkerLifetime;
    private final Duration maxIdleTime;
    private final Duration leaseTimeout;
    private final Duration requestTimeout;
    private final boolean destroyProcessTree;
    private final @Nullable WarmupAction warmupAction;
    private final List<ResetHook> resetHooks;
    private final PoolDiagnosticsListener diagnosticsListener;
    private final Clock clock;
    private final RequestTimeoutSchedulerFactory requestTimeoutSchedulerFactory;
    private final boolean invariantChecksEnabled;

    private ProcessPoolConfig(Builder builder) {
        this.workerCommand = builder.workerCommand;
        this.workerOptions = builder.normalisedWorkerOptions();
        this.minSize = builder.minSize;
        this.maxSize = builder.maxSize;
        this.maxQueueDepth = builder.maxQueueDepth;
        this.maxRequestsPerWorker = builder.maxRequestsPerWorker;
        this.maxWorkerLifetime = builder.maxWorkerLifetime;
        this.maxIdleTime = builder.maxIdleTime;
        this.leaseTimeout = builder.leaseTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.destroyProcessTree = builder.destroyProcessTree;
        this.warmupAction = builder.warmupAction;
        this.resetHooks = List.copyOf(builder.resetHooks);
        this.diagnosticsListener = builder.diagnosticsListener;
        this.clock = builder.clock;
        this.requestTimeoutSchedulerFactory = builder.requestTimeoutSchedulerFactory;
        this.invariantChecksEnabled = builder.invariantChecksEnabled;
    }

    /**
     * Creates a new builder initialised with defaults derived from {@code command}. The {@link Builder} enforces input
     * validation and produces an immutable via {@link Builder#build()}.
     *
     * @param command command executed for every pooled worker
     *
     * @return a new builder instance
     *
     * @throws NullPointerException when {@code command} is {@code null}
     */
    public static Builder builder(CommandDefinition command) {
        return new Builder(command);
    }

    /**
     * Command that the pool launches when creating workers. The definition is copied defensively during construction.
     *
     * @return immutable command description for pooled workers
     */
    public CommandDefinition workerCommand() {
        return workerCommand;
    }

    /**
     * Execution options applied to every worker launch. Options control PTY usage, capture policies, idle timeouts, and
     * shutdown behaviour for the underlying {@link com.github.ulviar.icli.core.InteractiveSession}.
     * The {@link #destroyProcessTree()} preference is applied to the options during construction so forced shutdowns
     * honour the pool-level policy.
     *
     * @return immutable execution options
     */
    public ExecutionOptions workerOptions() {
        return workerOptions;
    }

    /**
     * Minimum number of workers the pool maintains while it remains open. A value of zero disables pre-warming.
     *
     * @return non-negative minimum size
     */
    public int minSize() {
        return minSize;
    }

    /**
     * Upper bound on concurrently allocated workers (idle + leased). The pool never exceeds this figure even when many
     * callers are waiting.
     *
     * @return positive maximum worker count
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Maximum number of callers allowed in the wait queue. {@link Integer#MAX_VALUE} indicates an unbounded queue.
     *
     * @return non-negative queue capacity
     */
    public int maxQueueDepth() {
        return maxQueueDepth;
    }

    /**
     * Maximum number of requests served by a worker before it is retired. Large values keep workers alive longer,
     * smaller values bias toward freshness.
     *
     * @return positive reuse limit
     */
    public int maxRequestsPerWorker() {
        return maxRequestsPerWorker;
    }

    /**
     * Maximum wall-clock lifetime for a worker. {@link Duration#ZERO} disables the lifetime check.
     *
     * @return non-null duration
     */
    public Duration maxWorkerLifetime() {
        return maxWorkerLifetime;
    }

    /**
     * Maximum idle time before a worker is retired. {@link Duration#ZERO} disables idle retirement.
     *
     * @return non-null duration
     */
    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Maximum time a caller waits when acquiring a worker from the pool. The timeout applies to queueing before a lease
     * is granted; once a caller obtains a worker, {@link #requestTimeout()} governs how long the request may run.
     */
    public Duration leaseTimeout() {
        return leaseTimeout;
    }

    /**
     * Deadline for work performed under a single lease. When the timeout elapses the pool fails the request and retires
     * the worker via the configured {@link RequestTimeoutSchedulerFactory}.
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * Indicates whether the pool should terminate the entire worker process tree during forced shutdown. The value is
     * applied to {@link #workerOptions()} so that both explicit pool closures and request timeouts follow the same
     * termination policy.
     *
     * @return {@code true} to terminate descendants, {@code false} to leave them running
     */
    public boolean destroyProcessTree() {
        return destroyProcessTree;
    }

    public Optional<WarmupAction> warmupAction() {
        return Optional.ofNullable(warmupAction);
    }

    /**
     * Ordered hooks invoked after each request to reset and verify worker state. Hooks run synchronously on the thread
     * returning the lease; expensive logic should therefore be avoided.
     *
     * @return immutable list of reset hooks
     */
    public List<ResetHook> resetHooks() {
        return resetHooks;
    }

    /**
     * Diagnostics listener notified about pool lifecycle events. The listener is invoked synchronously on caller
     * threads and must not block.
     *
     * @return diagnostics listener (never {@code null}; defaults to {@link PoolDiagnosticsListener#noOp()})
     */
    public PoolDiagnosticsListener diagnosticsListener() {
        return diagnosticsListener;
    }

    /**
     * Clock used for time-based policies. The builder defaults to {@link Clock#systemUTC()} but allows injecting a
     * deterministic clock for testing.
     *
     * @return clock consulted by the pool
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns {@code true} when the pool state machine asserts its invariants on every transition. Disabling the
     * checks removes a layer of defensive validation and should only be used in tuned production deployments.
     *
     * @return whether invariant checks are enabled
     */
    public boolean invariantChecksEnabled() {
        return invariantChecksEnabled;
    }

    RequestTimeoutSchedulerFactory requestTimeoutSchedulerFactory() {
        return requestTimeoutSchedulerFactory;
    }

    public static final class Builder {

        private static final Duration DEFAULT_MAX_WORKER_LIFETIME = Duration.ofMinutes(30);
        private static final Duration DEFAULT_MAX_IDLE_TIME = Duration.ofMinutes(5);
        private static final Duration DEFAULT_LEASE_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);

        private final CommandDefinition workerCommand;
        private ExecutionOptions workerOptions =
                ExecutionOptions.builder().idleTimeout(Duration.ZERO).build();
        private int minSize;
        private int maxSize = defaultMaxSize();
        private int maxQueueDepth = Integer.MAX_VALUE;
        private int maxRequestsPerWorker = 1_000;
        private Duration maxWorkerLifetime = DEFAULT_MAX_WORKER_LIFETIME;
        private Duration maxIdleTime = DEFAULT_MAX_IDLE_TIME;
        private Duration leaseTimeout = DEFAULT_LEASE_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private boolean destroyProcessTree = workerOptions.destroyProcessTree();
        private @Nullable WarmupAction warmupAction;
        private final List<ResetHook> resetHooks = new ArrayList<>();
        private PoolDiagnosticsListener diagnosticsListener = PoolDiagnosticsListener.noOp();
        private Clock clock = Clock.systemUTC();
        private RequestTimeoutSchedulerFactory requestTimeoutSchedulerFactory =
                RequestTimeoutSchedulerFactory.defaultFactory();
        private boolean invariantChecksEnabled = true;

        private Builder(CommandDefinition command) {
            this.workerCommand = Objects.requireNonNull(command, "workerCommand");
        }

        private static int defaultMaxSize() {
            int processors = Runtime.getRuntime().availableProcessors();
            int half = Math.max(processors / 2, 1);
            return Math.min(half, 8);
        }

        /**
         * Overrides the {@link ExecutionOptions} used to launch each worker. The builder copies the options
         * defensively. The {@code destroyProcessTree} flag is mirrored to {@link #destroyProcessTree(boolean)} so the
         * pool honours the same shutdown strategy by default.
         *
         * @param options execution options for worker launches
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code options} is {@code null}
         */
        public Builder workerOptions(ExecutionOptions options) {
            this.workerOptions = Objects.requireNonNull(options, "workerOptions");
            this.destroyProcessTree = options.destroyProcessTree();
            return this;
        }

        /**
         * Sets the minimum number of workers kept alive while the pool is open. Values below zero are rejected.
         *
         * @param value desired minimum size
         *
         * @return this builder
         *
         * @throws IllegalArgumentException when {@code value} is negative
         */
        public Builder minSize(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("minSize must be >= 0");
            }
            this.minSize = value;
            return this;
        }

        /**
         * Sets the absolute maximum number of workers that may exist at once. The value must be strictly positive.
         *
         * @param value maximum worker count
         *
         * @return this builder
         *
         * @throws IllegalArgumentException when {@code value} is not positive
         */
        public Builder maxSize(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxSize must be > 0");
            }
            this.maxSize = value;
            return this;
        }

        /**
         * Caps the number of waiting callers. Zero forces non-blocking behaviour once all workers are leased.
         *
         * @param value queue capacity; {@link Integer#MAX_VALUE} leaves the queue unbounded
         *
         * @return this builder
         *
         * @throws IllegalArgumentException when {@code value} is negative
         */
        public Builder maxQueueDepth(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxQueueDepth must be >= 0");
            }
            this.maxQueueDepth = value;
            return this;
        }

        /**
         * Limits how many requests an individual worker may serve before it is retired and replaced. Values must be
         * strictly positive.
         *
         * @param value reuse threshold
         *
         * @return this builder
         *
         * @throws IllegalArgumentException when {@code value} is not positive
         */
        public Builder maxRequestsPerWorker(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxRequestsPerWorker must be > 0");
            }
            this.maxRequestsPerWorker = value;
            return this;
        }

        /**
         * Sets the maximum wall-clock lifetime for a worker. {@code value} must be non-null; use {@code Duration.ZERO}
         * to disable the lifetime limit.
         *
         * @param value lifetime threshold
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code value} is {@code null}
         */
        public Builder maxWorkerLifetime(Duration value) {
            this.maxWorkerLifetime = Objects.requireNonNull(value, "maxWorkerLifetime");
            return this;
        }

        /**
         * Sets the maximum idle time for a worker before it is retired. {@code Duration.ZERO} disables idle retirement.
         *
         * @param value idle threshold
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code value} is {@code null}
         */
        public Builder maxIdleTime(Duration value) {
            this.maxIdleTime = Objects.requireNonNull(value, "maxIdleTime");
            return this;
        }

        /**
         * Sets the maximum time callers wait while acquiring a worker. {@code value} must be non-null and
         * non-negative; use {@link Duration#ZERO} to request a non-blocking acquisition.
         */
        public Builder leaseTimeout(Duration value) {
            Duration timeout = Objects.requireNonNull(value, "leaseTimeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("leaseTimeout must be >= 0");
            }
            this.leaseTimeout = timeout;
            return this;
        }

        /**
         * Specifies how long a worker may execute a single request before timing out. {@code value} must be non-null;
         * use {@link Duration#ZERO} to disable automatic request deadlines.
         */
        public Builder requestTimeout(Duration value) {
            Duration timeout = Objects.requireNonNull(value, "requestTimeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("requestTimeout must be >= 0");
            }
            this.requestTimeout = timeout;
            return this;
        }

        /**
         * Controls whether {@link ProcessPool} destroys the entire process tree when forcefully shutting down a worker.
         * By default the value mirrors {@link ExecutionOptions#destroyProcessTree()} supplied via {@link
         * #workerOptions(ExecutionOptions)}. Any override is applied to {@link #workerOptions(ExecutionOptions)} during
         * {@link #build()} so pooled sessions and forced shutdowns remain consistent.
         *
         * @param value {@code true} to destroy the process tree, {@code false} to target only the worker process
         *
         * @return this builder
         */
        public Builder destroyProcessTree(boolean value) {
            this.destroyProcessTree = value;
            return this;
        }

        /**
         * Registers a warm-up action executed immediately after each worker is launched and before it becomes visible
         * to callers. Warm-up failures prevent the worker from entering the pool and surface as {@link
         * ServiceUnavailableException} from the acquisition attempt.
         *
         * @param action optional warm-up callback; pass {@code null} to clear any previous action
         *
         * @return this builder
         */
        public Builder warmupAction(@Nullable WarmupAction action) {
            this.warmupAction = action;
            return this;
        }

        /**
         * Appends a reset hook executed after each request. Hooks run in registration order and may retire the worker
         * if they detect inconsistent state.
         *
         * @param hook hook to add; must not be {@code null}
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code hook} is {@code null}
         */
        public Builder addResetHook(ResetHook hook) {
            this.resetHooks.add(Objects.requireNonNull(hook, "hook"));
            return this;
        }

        /**
         * Overrides the diagnostics listener notified about major pool events. The default listener drops all events.
         *
         * @param listener listener implementation
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code listener} is {@code null}
         */
        public Builder diagnosticsListener(PoolDiagnosticsListener listener) {
            this.diagnosticsListener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        /**
         * Overrides the clock used for lifetime, idle, and deadline calculations. Primarily intended for testing.
         *
         * @param value clock instance
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code value} is {@code null}
         */
        public Builder clock(Clock value) {
            this.clock = Objects.requireNonNull(value, "clock");
            return this;
        }

        /**
         * Overrides the factory used to create the request-timeout scheduler. The scheduler is responsible for firing
         * callbacks when a lease exceeds the configured {@link #requestTimeout()}. Custom factories allow tests to plug
         * in deterministic schedulers or production deployments to integrate with existing scheduling infrastructure.
         *
         * @param factory scheduler factory
         *
         * @return this builder
         *
         * @throws NullPointerException when {@code factory} is {@code null}
         */
        public Builder requestTimeoutSchedulerFactory(RequestTimeoutSchedulerFactory factory) {
            this.requestTimeoutSchedulerFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        /**
         * Enables or disables invariant assertions performed by the pool state machine. The default is {@code true}.
         * Disabling the checks can reduce contention in production once the system has been validated.
         *
         * @param value {@code true} to perform invariant checks, {@code false} to skip them
         *
         * @return this builder
         */
        public Builder invariantChecksEnabled(boolean value) {
            this.invariantChecksEnabled = value;
            return this;
        }

        /**
         * Validates the supplied settings and produces an immutable {@link ProcessPoolConfig}. The builder may be
         * reused after calling this method.
         *
         * @return immutable configuration
         *
         * @throws IllegalArgumentException when {@link #minSize(int)} exceeds {@link #maxSize(int)}
         */
        public ProcessPoolConfig build() {
            if (minSize > maxSize) {
                throw new IllegalArgumentException("minSize must be <= maxSize");
            }
            return new ProcessPoolConfig(this);
        }

        private ExecutionOptions normalisedWorkerOptions() {
            ExecutionOptions options = workerOptions;
            if (options.destroyProcessTree() != destroyProcessTree) {
                options =
                        options.derive().destroyProcessTree(destroyProcessTree).build();
            }
            return options;
        }
    }
}
