/**
 * Hook and scheduler contracts that allow callers to customise how {@link
 * com.github.ulviar.icli.core.pool.api.ProcessPool} prepares, resets, and supervises pooled workers. Implementations
 * in this package are lightweight functional interfaces; callers may supply their own strategy objects to integrate
 * domain-specific logic without modifying core pool behaviour.
 */
@NotNullByDefault
package com.github.ulviar.icli.core.pool.api.hooks;

import org.jetbrains.annotations.NotNullByDefault;
