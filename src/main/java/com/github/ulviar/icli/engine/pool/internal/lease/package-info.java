/**
 * Internal lease infrastructure shared by the pool runtime. The classes in this package coordinate {@link
 * com.github.ulviar.icli.engine.pool.api.WorkerLease} lifecycles, expose immutable {@link
 * com.github.ulviar.icli.engine.pool.api.LeaseScope} snapshots, and enforce request-level timeouts without leaking the
 * outer {@link com.github.ulviar.icli.engine.pool.api.ProcessPool} synchronisation primitives.
 *
 * <p>Types are package-private implementation details; callers interact with them indirectly through the public pool
 * API. Documentation captures invariants relied on by neighbouring packages so future refactors preserve the behaviour.
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.internal.lease;

import org.jetbrains.annotations.NotNullByDefault;
