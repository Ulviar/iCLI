/**
 * Public entry points for the pooled interactive process runtime. The package exposes {@link
 * com.github.ulviar.icli.engine.pool.api.ProcessPool} and the accompanying configuration, diagnostics, and lease types
 * that callers use to borrow long-lived {@link com.github.ulviar.icli.engine.InteractiveSession} instances.
 *
 * <p>The API is designed for high-throughput automation that repeatedly executes commands against expensive
 * initialisers (interactive REPLs, build tools, language servers). Pools are constructed with {@link
 * com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig} and return {@link
 * com.github.ulviar.icli.engine.pool.api.WorkerLease} handles that wrap the underlying session and enforce the pool’s
 * lifecycle rules.
 *
 * <p>All types in this package are thread-safe unless noted otherwise. The pool itself accepts concurrent acquisition
 * calls, and every lease must be closed exactly once so the worker can be recycled or retired. Diagnostics and metrics
 * listeners execute on the caller thread to minimise coordination overhead; implementations are therefore expected to
 * complete quickly and to avoid blocking.
 *
 * @see com.github.ulviar.icli.engine.pool.api.ProcessPool
 * @see com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
 * @see com.github.ulviar.icli.engine.pool.api.WorkerLease
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.api;

import org.jetbrains.annotations.NotNullByDefault;
