package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;
import java.time.Instant;

/**
 * Outcome returned by {@link PoolState#completeRelease(PoolWorker, Instant, ReleasePlan)} expressing how the
 * worker was handled after release.
 */
public sealed interface ReleaseResult
        permits ReleaseResult.ReturnedToIdle, ReleaseResult.Assigned, ReleaseResult.Retired {

    static ReleaseResult returnedToIdle() {
        return ReturnedToIdle.INSTANCE;
    }

    static ReleaseResult assigned() {
        return Assigned.INSTANCE;
    }

    static ReleaseResult retired(RetiredWorker retired) {
        return new Retired(retired);
    }

    enum ReturnedToIdle implements ReleaseResult {
        INSTANCE
    }

    enum Assigned implements ReleaseResult {
        INSTANCE
    }

    record Retired(RetiredWorker retired) implements ReleaseResult {}
}
