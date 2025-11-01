package com.github.ulviar.icli.core.pool.internal.runtime;

import com.github.ulviar.icli.core.pool.api.WorkerRetirementReason;
import java.util.Objects;

/**
 * Result returned by collaborators that decide whether a worker must be retired after completing a request. The sealed
 * hierarchy provides two outcomes:
 *
 * <ul>
 *     <li>{@link RetireDecision#keep()} — the worker remains in the pool.</li>
 *     <li>{@link RetireDecision#retire(WorkerRetirementReason)} — the worker must be retired for the supplied reason.
 *     </li>
 * </ul>
 */
public sealed interface RetireDecision permits RetireDecision.Keep, RetireDecision.Retire {

    /**
     * Indicates that the worker should remain available for reuse.
     */
    static RetireDecision keep() {
        return Keep.INSTANCE;
    }

    /**
     * Indicates that the worker must be retired for the supplied reason.
     *
     * @param reason reason that motivated retirement
     */
    static RetireDecision retire(WorkerRetirementReason reason) {
        return new Retire(Objects.requireNonNull(reason, "reason"));
    }

    /**
     * @return {@code true} when the caller must retire the worker
     */
    default boolean retire() {
        return this instanceof Retire;
    }

    /**
     * Keeps the worker available for future leases.
     */
    final class Keep implements RetireDecision {
        private static final Keep INSTANCE = new Keep();

        private Keep() {}
    }

    /**
     * Signals that the worker must be retired and captures the {@link WorkerRetirementReason}.
     */
    record Retire(WorkerRetirementReason reason) implements RetireDecision {

        /**
         * @return reason that triggered the retirement
         */
        @Override
        public WorkerRetirementReason reason() {
            return reason;
        }
    }
}
