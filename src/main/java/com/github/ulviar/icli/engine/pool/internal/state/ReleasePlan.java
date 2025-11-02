package com.github.ulviar.icli.engine.pool.internal.state;

import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker;
import java.time.Instant;

/**
 * Plan returned by {@link PoolState#beginRelease(PoolWorker, Instant)} guiding how the pool should handle a
 * worker after a lease completes. The plan is evaluated outside the lock and fed back into {@link
 * PoolState#completeRelease(PoolWorker, java.time.Instant, ReleasePlan)}.
 */
public sealed interface ReleasePlan permits ReleasePlan.Ignore, ReleasePlan.Keep, ReleasePlan.Retire {

    static ReleasePlan ignore() {
        return Ignore.INSTANCE;
    }

    static ReleasePlan keep() {
        return Keep.INSTANCE;
    }

    static ReleasePlan retire(WorkerRetirementReason reason) {
        return new Retire(reason);
    }

    enum Ignore implements ReleasePlan {
        INSTANCE
    }

    enum Keep implements ReleasePlan {
        INSTANCE
    }

    public record Retire(WorkerRetirementReason reason) implements ReleasePlan {}
}
