package com.github.ulviar.icli.core.pool.internal.state;

import com.github.ulviar.icli.core.pool.api.WorkerRetirementReason;
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker;

/**
 * Captures a worker that left the pool along with the reason reported to diagnostics and reset hooks. Instances are
 * immutable, allowing {@link PoolState} to pass them safely to listeners after releasing its coordination lock. The
 * worker reference remains valid until the caller closes the associated
 * {@link com.github.ulviar.icli.core.InteractiveSession}.
 */
public record RetiredWorker(PoolWorker worker, WorkerRetirementReason reason) {}
