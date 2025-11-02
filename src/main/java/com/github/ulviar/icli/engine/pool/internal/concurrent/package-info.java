/**
 * Lock-coordinated concurrency primitives that back the process pool state machine.
 * <p>
 * Types in this package assume callers hold a shared {@link java.util.concurrent.locks.ReentrantLock} while invoking
 * their mutating operations so worker queueing and lifecycle transitions remain deterministic under contention. Where
 * deadlines apply, values are expressed as absolute {@link java.lang.System .nanoTime()} instants with {@code 0}
 * reserved for "wait indefinitely"; see {@link com.github.ulviar.icli.engine.pool.internal.concurrent.util.Deadline}
 * for the shared implementation.
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.internal.concurrent;

import org.jetbrains.annotations.NotNullByDefault;
