package com.github.ulviar.icli.engine.pool.api;

/**
 * Describes whether {@link ProcessPool#acquireWithPreference(PreferredWorker, java.time.Duration)} should favour a
 * specific worker id or accept any healthy worker. The type is immutable and thread-safe; callers may reuse instances
 * freely.
 *
 * <p><strong>Usage example</strong></p>
 *
 * <pre>{@code
 * PreferredWorker preference = PreferredWorker.specific(previousLease.scope().workerId());
 * try (WorkerLease lease = pool.acquireWithPreference(preference, Duration.ofSeconds(5))) {
 *     // reuse same worker when available
 * }
 * }</pre>
 *
 * @apiNote Use {@link #any()} for best-effort acquisition without stickiness or {@link #specific(int)} to bias
 * towards a particular worker id obtained from {@link LeaseScope#workerId()}.
 *
 * @implSpec Instances created via {@link #specific(int)} always carry non-negative worker ids. {@link #any()} returns a
 * canonical sentinel shared across callers.
 */
public final class PreferredWorker {

    private static final PreferredWorker ANY = new PreferredWorker(false, -1);

    private final boolean hasSpecificWorker;
    private final int workerId;

    private PreferredWorker(boolean hasSpecificWorker, int workerId) {
        this.hasSpecificWorker = hasSpecificWorker;
        this.workerId = workerId;
    }

    /**
     * Returns a sentinel indicating that any healthy worker is acceptable.
     *
     * @return sentinel preference shared across callers
     */
    public static PreferredWorker any() {
        return ANY;
    }

    /**
     * Returns a preference that requests the provided worker id whenever it is idle. The request is best effort: when
     * the worker is busy the pool falls back to any healthy worker.
     *
     * @param workerId identifier obtained from {@link LeaseScope#workerId()}
     * @return preference tied to the worker id
     * @throws IllegalArgumentException when {@code workerId} is negative
     */
    public static PreferredWorker specific(int workerId) {
        if (workerId < 0) {
            throw new IllegalArgumentException("workerId must not be negative");
        }
        return new PreferredWorker(true, workerId);
    }

    /**
     * Indicates whether this preference references a concrete worker id.
     *
     * @return {@code true} when {@link #workerId()} can be accessed
     */
    public boolean hasSpecificWorker() {
        return hasSpecificWorker;
    }

    /**
     * Returns the preferred worker id.
     *
     * @return worker identifier provided at construction
     * @throws IllegalStateException when invoked on {@link #any()}
     */
    public int workerId() {
        if (!hasSpecificWorker) {
            throw new IllegalStateException("preference does not target a specific worker");
        }
        return workerId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PreferredWorker other = (PreferredWorker) obj;
        return hasSpecificWorker == other.hasSpecificWorker && workerId == other.workerId;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(hasSpecificWorker);
        result = 31 * result + Integer.hashCode(workerId);
        return result;
    }

    @Override
    public String toString() {
        return hasSpecificWorker ? "PreferredWorker[" + workerId + "]" : "PreferredWorker[any]";
    }
}
