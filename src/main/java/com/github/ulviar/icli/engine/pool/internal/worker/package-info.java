/**
 * Internal representation of pooled worker processes. Types in this package are responsible for tracking the lifecycle
 * of a single interactive process (lease state, reuse counters, retirement reasons) while the enclosing pool
 * coordinates scheduling and diagnostics.
 *
 * <p>The public pool API never exposes these classes directly; documentation exists to preserve invariants relied upon
 * by neighbouring packages such as {@code internal.state} and {@code internal.lease}.
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.internal.worker;

import org.jetbrains.annotations.NotNullByDefault;
