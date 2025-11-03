package com.github.ulviar.icli.engine.pool.api;

import com.github.ulviar.icli.client.ClientScheduler;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.InteractiveSession;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.pool.api.hooks.RequestTimeoutScheduler;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;
import com.github.ulviar.icli.engine.pool.api.hooks.WarmupAction;
import com.github.ulviar.icli.engine.pool.internal.concurrent.util.Deadline;
import com.github.ulviar.icli.engine.pool.internal.lease.ActiveLeaseRegistry;
import com.github.ulviar.icli.engine.pool.internal.lease.DefaultLeaseScope;
import com.github.ulviar.icli.engine.pool.internal.lease.DefaultWorkerLease;
import com.github.ulviar.icli.engine.pool.internal.lease.LeaseCallbacks;
import com.github.ulviar.icli.engine.pool.internal.runtime.ResetHookRunner;
import com.github.ulviar.icli.engine.pool.internal.runtime.RetireDecision;
import com.github.ulviar.icli.engine.pool.internal.state.AcquireResult;
import com.github.ulviar.icli.engine.pool.internal.state.DrainStatus;
import com.github.ulviar.icli.engine.pool.internal.state.LaunchDiscardReason;
import com.github.ulviar.icli.engine.pool.internal.state.LaunchResult;
import com.github.ulviar.icli.engine.pool.internal.state.PoolState;
import com.github.ulviar.icli.engine.pool.internal.state.ReleasePlan;
import com.github.ulviar.icli.engine.pool.internal.state.ReleaseResult;
import com.github.ulviar.icli.engine.pool.internal.state.RetiredWorker;
import com.github.ulviar.icli.engine.pool.internal.state.WorkerRetirementPolicy;
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates a fleet of long-lived {@link InteractiveSession} instances so callers can reuse expensive interactive
 * workers safely. Each successful acquisition returns a {@link WorkerLease} that exposes the underlying session, the
 * {@link ExecutionOptions} used to launch it, and request-scoped metadata required by diagnostics and reset hooks.
 *
 * <p>The pool is optimised for concurrent callers: acquisition attempts may block, queue, or fail fast depending on
 * the configured limits and timeouts. Internally the pool preserves FIFO ordering for waiters, enforces request
 * deadlines, and retires workers whose reuse count, lifetime, or idle time crosses the thresholds specified in {@link
 * ProcessPoolConfig}. All public methods are thread-safe.
 *
 * <p>Instances are created through {@link #create(ProcessEngine, ProcessPoolConfig)}. Pool shutdown is a two-step
 * process: invoke {@link #close()} to signal that no new leases should be granted and then call {@link
 * #drain(Duration)} to wait for active work to finish and for internal resources (notably the request-timeout
 * scheduler) to be released.
 */
public final class ProcessPool implements AutoCloseable {

    private static final Duration BASE_PREWARM_BACKOFF = Duration.ofMillis(50);
    private static final Duration MAX_PREWARM_BACKOFF = Duration.ofMillis(500);

    @FunctionalInterface
    interface PrewarmSleeper {

        void sleep(Duration duration) throws InterruptedException;
    }

    private static final PrewarmSleeper DEFAULT_PREWARM_SLEEPER = ProcessPool::sleepFor;
    private static final AtomicReference<PrewarmSleeper> PREWARM_SLEEPER =
            new AtomicReference<>(DEFAULT_PREWARM_SLEEPER);

    private static final PoolMetrics UNINITIALISED_METRICS =
            new PoolMetrics(-1, -1, -1, -1, -1, -1, -1, -1L, -1L, -1L, -1L);

    private final ProcessEngine engine;
    private final ProcessPoolConfig config;
    private final PoolDiagnosticsListener diagnostics;
    private final ResetHookRunner resetHookRunner;
    private final RequestTimeoutScheduler requestTimeouts;
    private final PoolState state;
    private final ActiveLeaseRegistry activeLeases = new ActiveLeaseRegistry();
    private final LeaseCallbacks leaseCallbacks = new LeaseCallbacks() {
        @Override
        public void registerActiveLease(int workerId, DefaultLeaseScope scope) {
            ProcessPool.this.registerActiveLease(workerId, scope);
        }

        @Override
        public void resetLease(PoolWorker worker, DefaultLeaseScope scope, ResetRequest request) {
            ProcessPool.this.resetLease(worker, scope, request);
        }

        @Override
        public void releaseLease(PoolWorker worker, DefaultLeaseScope scope, UUID requestId) {
            ProcessPool.this.releaseLease(worker, scope, requestId);
        }
    };
    private final Object prewarmBackoffLock = new Object();
    private final Object metricsLock = new Object();
    private int consecutivePrewarmFailures;
    private volatile PoolMetrics lastPublishedMetrics = UNINITIALISED_METRICS;

    private ProcessPool(ProcessEngine engine, ProcessPoolConfig config) {
        this.engine = engine;
        this.config = config;
        this.diagnostics = config.diagnosticsListener();
        this.resetHookRunner = new ResetHookRunner(config.resetHooks(), diagnostics);
        WorkerRetirementPolicy retirementPolicy = new WorkerRetirementPolicy(config);
        this.requestTimeouts = config.requestTimeoutSchedulerFactory().create();
        this.state = new PoolState(config, retirementPolicy);

        ensureMinimumSize();
        publishMetrics();
    }

    /**
     * Constructs a new pool and immediately begins pre-warming workers to satisfy the configured minimum size. The
     * method never returns {@code null}.
     *
     * @param engine process runtime used to launch interactive sessions
     * @param config behavioural and sizing policy for the pool
     *
     * @return a fully initialised pool
     */
    public static ProcessPool create(ProcessEngine engine, ProcessPoolConfig config) {
        return new ProcessPool(engine, config);
    }

    /**
     * Acquires a worker using the default {@linkplain ProcessPoolConfig#leaseTimeout() lease timeout}. The call blocks
     * until a worker becomes available, the pool closes, or the timeout elapses.
     *
     * @return a {@link WorkerLease} that must be closed to return the worker to the pool
     *
     * @throws ServiceUnavailableException when the wait times out, the pool is closing or terminated, or worker launch
     *                                     fails
     */
    public WorkerLease acquire() {
        return acquire(config.leaseTimeout());
    }

    /**
     * Acquires a worker, waiting up to {@code timeout} for one to become available.
     *
     * <p>Passing {@link Duration#ZERO zero} requests a non-blocking attempt that succeeds immediately when an idle
     * worker exists and otherwise throws {@link ServiceUnavailableException} without joining the wait queue. Negative
     * timeouts are rejected.
     *
     * @param timeout maximum time to wait; {@code Duration.ZERO} performs a non-blocking probe
     *
     * @return a {@link WorkerLease} wrapping the borrowed worker
     *
     * @throws IllegalArgumentException    when {@code timeout} is negative
     * @throws ServiceUnavailableException when the wait times out, the pool is closing or terminated, or the pool
     *                                     cannot launch the worker reserved for this caller
     */
    public WorkerLease acquire(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long deadlineNanos = timeout.isZero() ? 0 : Deadline.toAbsoluteTimeout(timeout);
        boolean waitAllowed = !timeout.isZero();

        while (true) {
            AcquireResult result = state.acquire(deadlineNanos, waitAllowed);
            publishMetrics();

            retireWorkers(result.retired());
            ensureMinimumSize();

            switch (result) {
                case AcquireResult.Leased leased -> {
                    PoolWorker worker = leased.worker();
                    DefaultLeaseScope scope = leased.scope();
                    DefaultWorkerLease lease = new DefaultWorkerLease(leaseCallbacks, worker, scope);
                    startRequestDeadline(worker, scope);
                    diagnostics.leaseAcquired(worker.id());
                    return lease;
                }
                case AcquireResult.LaunchReserved reserved -> {
                    int workerId = reserved.workerId();
                    PoolWorker worker;
                    try {
                        worker = launchWorker(workerId);
                    } catch (ServiceUnavailableException ex) {
                        handlePrewarmFailure();
                        throw ex;
                    }

                    LaunchResult launchResult = state.onLaunchSuccess(worker);
                    publishMetrics();
                    if (launchResult instanceof LaunchResult.Discarded discarded) {
                        retireWorker(worker, retirementCauseFor(discarded.reason()));
                        ensureMinimumSize();
                        throw new ServiceUnavailableException(messageForDiscard(discarded.reason()));
                    }

                    diagnostics.workerCreated(workerId);
                    publishMetrics();
                }
                case AcquireResult.QueueRejected rejected -> {
                    diagnostics.queueRejected(
                            rejected.details().pendingWaiters(),
                            rejected.details().capacity());
                    throw rejected.error();
                }
                case AcquireResult.Failed failed -> throw failed.error();
                default -> {}
            }
        }
    }

    /**
     * Asynchronously acquires a worker using the default {@linkplain ProcessPoolConfig#leaseTimeout() lease timeout}.
     *
     * @param scheduler executor used to run the blocking acquisition
     *
     * @return a {@link CompletionStage} that completes with the lease or exceptionally with {@link
     * ServiceUnavailableException}
     */
    public CompletionStage<WorkerLease> acquireAsync(ClientScheduler scheduler) {
        return acquireAsync(scheduler, config.leaseTimeout());
    }

    /**
     * Asynchronously acquires a worker by scheduling the blocking call on the supplied {@link ClientScheduler}.
     *
     * @param scheduler executor used to run the acquisition attempt
     * @param timeout   maximum time to wait; behaves identically to {@link #acquire(Duration)}
     *
     * @return a {@link CompletionStage} that completes with the lease or exceptionally with {@link
     * ServiceUnavailableException}
     */
    public CompletionStage<WorkerLease> acquireAsync(ClientScheduler scheduler, Duration timeout) {
        return scheduler.submit(() -> acquire(timeout));
    }

    private void resetLease(PoolWorker worker, DefaultLeaseScope scope, ResetRequest request) {
        RetireDecision decision = resetHookRunner.run(worker, scope, request);
        if (decision instanceof RetireDecision.Retire retire) {
            worker.requestRetire(retire.reason());
        }
    }

    private void releaseLease(PoolWorker worker, DefaultLeaseScope scope, UUID requestId) {
        cancelRequestDeadline(worker);
        removeActiveLease(worker.id());
        releaseWorker(worker, scope, ResetRequest.leaseCompleted(requestId));
    }

    /**
     * Returns an immutable snapshot of the pool’s utilisation and diagnostic counters. The snapshot reflects the
     * state at the instant the method executes and is safe to cache for later inspection.
     *
     * @return current metrics describing worker counts, queue depth, and lifetime counters
     */
    public PoolMetrics snapshot() {
        return state.snapshot();
    }

    /**
     * Initiates pool shutdown. New acquisition attempts fail immediately, but active leases remain valid until callers
     * close them or they exceed the configured request timeout. The method is idempotent.
     *
     * <p>To wait for the pool to release every worker and to dispose internal helpers, invoke {@link #drain(Duration)}
     * after calling {@code close()}.
     */
    @Override
    public void close() {
        if (state.markClosing()) {
            diagnostics.poolDraining();
            publishMetrics();
        }
    }

    /**
     * Waits for the pool to release all workers and retire outstanding sessions. The request-timeout scheduler is
     * closed once the pool reaches the terminated state.
     *
     * @param timeout maximum time to wait before giving up; {@link Duration#ZERO} performs a non-blocking status check
     *
     * @return {@code true} when the pool drained and terminated before the timeout elapsed, or {@code false} otherwise
     */
    public boolean drain(Duration timeout) {
        close();
        long now = System.nanoTime();
        long deadline = timeout.isZero() ? now : Deadline.toAbsoluteTimeout(timeout, now);
        List<PoolWorker> retiring = new ArrayList<>();
        DrainStatus status = state.drain(deadline, retiring);
        publishMetrics();

        for (PoolWorker worker : retiring) {
            retireWorker(worker, WorkerRetirementReason.DRAIN);
        }

        if (status.completed() && status.terminatedNow()) {
            diagnostics.poolTerminated();
        }

        if (status.completed()) {
            requestTimeouts.close();
        }
        return status.completed();
    }

    private void releaseWorker(PoolWorker worker, DefaultLeaseScope scope, ResetRequest request) {
        Instant now = config.clock().instant();
        ReleasePlan initialPlan = state.beginRelease(worker, now);
        publishMetrics();
        if (initialPlan instanceof ReleasePlan.Ignore) {
            return;
        }

        ReleasePlan effectivePlan = initialPlan;

        if (initialPlan instanceof ReleasePlan.Keep) {
            RetireDecision decision = resetHookRunner.run(worker, scope, request);
            if (decision instanceof RetireDecision.Retire retire) {
                worker.requestRetire(retire.reason());
                effectivePlan = ReleasePlan.retire(retire.reason());
            }
        }

        ReleaseResult outcome = state.completeRelease(worker, now, effectivePlan);
        diagnostics.leaseReleased(worker.id());
        publishMetrics();

        if (outcome instanceof ReleaseResult.Retired retired) {
            RetiredWorker retiredWorker = retired.retired();
            retireWorker(retiredWorker.worker(), retiredWorker.reason());
            ensureMinimumSize();
        }
    }

    private PoolWorker launchWorker(int workerId) {
        CommandDefinition command = config.workerCommand();
        ExecutionOptions options = config.workerOptions();
        InteractiveSession session;
        try {
            session = engine.startSession(command, options);
        } catch (RuntimeException ex) {
            state.onLaunchFailure(true);
            publishMetrics();
            diagnostics.workerFailed(workerId, ex);
            throw new ServiceUnavailableException("Failed to launch pooled worker", ex);
        }

        WarmupAction warmup = config.warmupAction().orElse(null);
        if (warmup != null) {
            try {
                warmup.perform(session);
            } catch (Exception ex) {
                closeQuietly(session);
                state.onLaunchFailure(true);
                publishMetrics();
                diagnostics.workerFailed(workerId, ex);
                throw new ServiceUnavailableException("Worker warmup failed", ex);
            }
        }

        return new PoolWorker(workerId, session, options, config.clock().instant());
    }

    private void retireWorkers(List<RetiredWorker> retired) {
        if (retired.isEmpty()) {
            return;
        }
        for (RetiredWorker entry : retired) {
            retireWorker(entry.worker(), entry.reason());
        }
    }

    private void retireWorker(PoolWorker worker, WorkerRetirementReason reason) {
        diagnostics.workerRetired(worker.id(), reason);
        state.recordRetirement();
        closeQuietly(worker.session());
        publishMetrics();
    }

    private void registerActiveLease(int workerId, DefaultLeaseScope scope) {
        activeLeases.register(workerId, scope);
    }

    @Nullable
    private DefaultLeaseScope removeActiveLease(int workerId) {
        return activeLeases.remove(workerId);
    }

    private void ensureMinimumSize() {
        if (config.minSize() <= 0) {
            return;
        }

        for (OptionalInt id = state.reserveNextForMinimum(); id.isPresent(); id = state.reserveNextForMinimum()) {

            publishMetrics();
            switch (prewarmOne(id.getAsInt())) {
                case CREATED -> resetPrewarmBackoff();
                case DISCARDED -> {
                    return;
                }
                case FAILED -> {
                    handlePrewarmFailure();
                    return;
                }
            }
        }
        resetPrewarmBackoff();
    }

    private PrewarmOutcome prewarmOne(int workerId) {
        try {
            PoolWorker worker = launchWorker(workerId);
            LaunchResult launchResult = state.onLaunchSuccess(worker);
            publishMetrics();
            if (launchResult instanceof LaunchResult.Discarded discarded) {
                retireWorker(worker, retirementCauseFor(discarded.reason()));
                return PrewarmOutcome.DISCARDED;
            }
            diagnostics.workerCreated(workerId);
            return PrewarmOutcome.CREATED;
        } catch (ServiceUnavailableException ex) {
            return PrewarmOutcome.FAILED;
        }
    }

    private enum PrewarmOutcome {
        CREATED,
        DISCARDED,
        FAILED
    }

    private void startRequestDeadline(PoolWorker worker, DefaultLeaseScope scope) {
        Duration timeout = config.requestTimeout();
        if (timeout.compareTo(Duration.ZERO) <= 0) {
            return;
        }
        requestTimeouts.schedule(
                worker.id(), scope.requestId(), timeout, () -> onLeaseTimeout(worker, scope.requestId()));
    }

    private void cancelRequestDeadline(PoolWorker worker) {
        requestTimeouts.cancel(worker.id());
    }

    private void onLeaseTimeout(PoolWorker worker, UUID requestId) {
        if (!requestTimeouts.complete(worker.id(), requestId)) {
            return;
        }
        DefaultLeaseScope scope = removeActiveLease(worker.id());
        if (scope == null) {
            return;
        }
        diagnostics.leaseTimedOut(worker.id(), requestId);
        diagnostics.workerFailed(
                worker.id(), new ServiceProcessingException("Lease " + requestId + " exceeded request timeout"));
        worker.requestRetire(WorkerRetirementReason.REQUEST_TIMEOUT);
        releaseWorker(worker, scope, ResetRequest.timedOut(requestId));
    }

    private void handlePrewarmFailure() {
        Duration delay;
        synchronized (prewarmBackoffLock) {
            consecutivePrewarmFailures = Math.min(consecutivePrewarmFailures + 1, 10);
            delay = computePrewarmDelay(consecutivePrewarmFailures);
        }
        sleepQuietly(delay);
    }

    private void resetPrewarmBackoff() {
        synchronized (prewarmBackoffLock) {
            consecutivePrewarmFailures = 0;
        }
    }

    private static Duration computePrewarmDelay(int failureCount) {
        long multiplier = Math.max(1, failureCount);
        long baseMillis = BASE_PREWARM_BACKOFF.toMillis();
        long millis = Math.min(MAX_PREWARM_BACKOFF.toMillis(), baseMillis * multiplier);
        return Duration.ofMillis(millis);
    }

    private void sleepQuietly(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            PREWARM_SLEEPER.get().sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void publishMetrics() {
        PoolMetrics metrics = state.snapshot();
        boolean emit;
        synchronized (metricsLock) {
            if (Objects.equals(lastPublishedMetrics, metrics)) {
                emit = false;
            } else {
                lastPublishedMetrics = metrics;
                emit = true;
            }
        }
        if (emit) {
            diagnostics.metricsUpdated(metrics);
        }
    }

    private static void sleepFor(Duration duration) throws InterruptedException {
        long millis = duration.toMillis();
        int nanos = (int) duration.minusMillis(millis).toNanos();
        Thread.sleep(millis, nanos);
    }

    static void setPrewarmSleeperForTests(PrewarmSleeper sleeper) {
        PREWARM_SLEEPER.set(sleeper);
    }

    static void resetPrewarmSleeperForTests() {
        PREWARM_SLEEPER.set(DEFAULT_PREWARM_SLEEPER);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static String messageForDiscard(LaunchDiscardReason reason) {
        return reason.message();
    }

    private static WorkerRetirementReason retirementCauseFor(LaunchDiscardReason reason) {
        return switch (reason) {
            case POOL_TERMINATED -> WorkerRetirementReason.POOL_TERMINATED;
            case POOL_CLOSING -> WorkerRetirementReason.POOL_CLOSING;
        };
    }
}
