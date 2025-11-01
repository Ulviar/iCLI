package com.github.ulviar.icli.core.pool.internal.lease;

import com.github.ulviar.icli.core.pool.api.hooks.RequestTimeoutScheduler;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link RequestTimeoutScheduler} used to enforce per-request deadlines. The scheduler runs callbacks on a
 * single daemon thread because timeouts trigger lightweight recovery logic that quickly hands control back to the pool.
 * Call {@link #close()} once the pool has fully drained to cancel outstanding tasks and release the thread.
 *
 * <p>Scheduling guarantees:
 * <ul>
 *     <li>Only the most recent lease for a worker retains an active timeout.</li>
 *     <li>Timeouts fire on a daemon thread with millisecond precision (minimum delay one millisecond).</li>
 *     <li>{@link #close()} cancels all outstanding tasks and shuts the scheduler down.</li>
 * </ul>
 */
public final class DefaultRequestTimeoutScheduler implements RequestTimeoutScheduler {

    private static final ThreadFactory THREAD_FACTORY = runnable ->
            Thread.ofPlatform().daemon(true).name("icli-request-timeouts", 0).unstarted(runnable);

    private static final long MIN_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1L);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);

    private final ConcurrentMap<Integer, Timeout> timeouts = new ConcurrentHashMap<>();

    /**
     * Schedules {@code onTimeout} after {@code timeout}. If another lease is registered for the same worker the
     * previous timeout is cancelled before the new one is scheduled.
     *
     * @param workerId  identifier of the worker being supervised
     * @param requestId request identifier associated with the lease
     * @param timeout   delay before {@code onTimeout} executes
     * @param onTimeout callback invoked when the timeout expires
     */
    @Override
    public void schedule(int workerId, UUID requestId, Duration timeout, Runnable onTimeout) {
        long delayNanos = toDelayNanos(timeout);
        timeouts.compute(workerId, (id, prev) -> {
            if (prev != null) {
                prev.future().cancel(false);
            }
            ScheduledFuture<?> f = executor.schedule(onTimeout, delayNanos, TimeUnit.NANOSECONDS);
            return new Timeout(requestId, f);
        });
    }

    /**
     * Cancels the outstanding timeout for {@code workerId}. No-op when the worker has no scheduled timeout.
     *
     * @param workerId identifier of the worker whose timeout should be cancelled
     */
    @Override
    public void cancel(int workerId) {
        Timeout timeout = timeouts.remove(workerId);
        if (timeout != null) {
            timeout.future().cancel(false);
        }
    }

    /**
     * Cancels the outstanding timeout if the provided {@code requestId} matches the one currently registered for
     * {@code workerId}.
     *
     * @param workerId  identifier of the worker whose timeout should be cleared
     * @param requestId identifier of the lease completing successfully
     *
     * @return {@code true} when the timeout was removed
     */
    @Override
    public boolean complete(int workerId, UUID requestId) {
        Timeout timeout = timeouts.get(workerId);
        if (timeout == null || !timeout.requestId().equals(requestId)) {
            return false;
        }
        if (timeouts.remove(workerId, timeout)) {
            timeout.future().cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Cancels every outstanding timeout and shuts down the scheduler. This method is idempotent.
     */
    @Override
    public void close() {
        for (Timeout timeout : timeouts.values()) {
            timeout.future().cancel(false);
        }
        timeouts.clear();
        executor.shutdownNow();
    }

    private static long toDelayNanos(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be non-negative: " + timeout);
        }
        long seconds = timeout.getSeconds();
        int nanos = timeout.getNano();
        try {
            long secondsNanos = Math.multiplyExact(seconds, 1_000_000_000L);
            long total = Math.addExact(secondsNanos, nanos);
            if (total == 0L) {
                return MIN_DELAY_NANOS;
            }
            return Math.max(total, MIN_DELAY_NANOS);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private record Timeout(UUID requestId, ScheduledFuture<?> future) {}
}
