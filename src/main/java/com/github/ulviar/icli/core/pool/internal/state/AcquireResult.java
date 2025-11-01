package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.api.ServiceUnavailableException;
import com.github.ulviar.icli.core.pool.internal.lease.DefaultLeaseScope;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * Outcome returned by {@link PoolState#acquire(long, boolean)} capturing whether a lease was granted, a launch was
 * reserved, or the attempt failed. Each variant also carries the list of workers retired while the lock was held so
 * callers can dispose them outside the critical section.
 */
public sealed interface AcquireResult
        permits AcquireResult.Leased,
                AcquireResult.LaunchReserved,
                AcquireResult.Failed,
                AcquireResult.QueueRejected,
                AcquireResult.None {

    static AcquireResult none() {
        return None.INSTANCE;
    }

    List<RetiredWorker> retired();

    static AcquireResult leased(PoolWorker worker, DefaultLeaseScope scope, List<RetiredWorker> retired) {
        return new Leased(worker, scope, retired);
    }

    static AcquireResult launchReserved(int workerId, List<RetiredWorker> retired) {
        return new LaunchReserved(workerId, retired);
    }

    static AcquireResult failed(ServiceUnavailableException error, List<RetiredWorker> retired) {
        return new Failed(error, retired);
    }

    static AcquireResult queueRejected(
            ServiceUnavailableException error, List<RetiredWorker> retired, QueueRejectionDetails details) {
        return new QueueRejected(error, retired, details);
    }

    /**
     * Successful acquisition that hands a worker to the caller.
     */
    record Leased(PoolWorker worker, DefaultLeaseScope scope, List<RetiredWorker> retired) implements AcquireResult {

        public Leased {
            retired = List.copyOf(retired);
        }
    }

    /**
     * Acquisition path that reserved a worker identifier for launch. The caller must attempt to launch the worker and
     * report the outcome.
     */
    record LaunchReserved(int workerId, List<RetiredWorker> retired) implements AcquireResult {

        public LaunchReserved {
            retired = List.copyOf(retired);
        }
    }

    /**
     * Acquisition failure that should be surfaced to the caller.
     */
    record Failed(ServiceUnavailableException error, List<RetiredWorker> retired) implements AcquireResult {

        @SuppressFBWarnings(
                value = "EI_EXPOSE_REP2",
                justification = "Exceptions are immutable diagnostics shared with callers")
        public Failed {
            retired = List.copyOf(retired);
        }

        @Override
        @SuppressFBWarnings(
                value = "EI_EXPOSE_REP",
                justification = "Exceptions are immutable diagnostics shared with callers")
        public ServiceUnavailableException error() {
            return error;
        }
    }

    /**
     * Acquisition failure because the wait queue reached capacity. Includes queue context for diagnostics.
     */
    record QueueRejected(ServiceUnavailableException error, List<RetiredWorker> retired, QueueRejectionDetails details)
            implements AcquireResult {

        @SuppressFBWarnings(
                value = "EI_EXPOSE_REP2",
                justification = "Exceptions are immutable diagnostics shared with callers")
        public QueueRejected {
            retired = List.copyOf(retired);
        }

        @Override
        @SuppressFBWarnings(
                value = "EI_EXPOSE_REP",
                justification = "Exceptions are immutable diagnostics shared with callers")
        public ServiceUnavailableException error() {
            return error;
        }
    }

    final class None implements AcquireResult {

        private static final None INSTANCE = new None();

        private None() {}

        @Override
        public List<RetiredWorker> retired() {
            return List.of();
        }
    }
}
