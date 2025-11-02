/**
 * Runtime helpers invoked during lease reset and worker retirement decisions. These types interpret the outcomes of
 * user-provided reset hooks, surface diagnostics, and communicate whether a worker must be removed from circulation.
 * They are internal implementation details; the public pool API observes their results through the higher-level state
 * machine.
 */
@NotNullByDefault
package com.github.ulviar.icli.engine.pool.internal.runtime;

import org.jetbrains.annotations.NotNullByDefault;
