package com.github.ulviar.icli.core;

import java.time.Duration;

/**
 * Receives callbacks about significant lifecycle events emitted by an {@link InteractiveSession}.
 *
 * <p>Observers are optional: the runtime uses {@link #NO_OP} when no listener is supplied through
 * {@link ExecutionOptions#sessionObserver()}. Implementations must be thread-safe because events may be delivered from
 * internal runtime threads that supervise idle timeouts and shutdown flows. Observers should return quickly — long
 * running or blocking work risks delaying enforcement of idle policies.
 */
public interface SessionLifecycleObserver {

    SessionLifecycleObserver NO_OP = (_, _) -> {
        // no-op
    };

    /**
     * Invoked immediately before the runtime terminates an interactive session due to inactivity.
     *
     * @param command     the command definition that created the session. Provides contextual information for logging
     *                    or diagnostics; observers must not attempt to mutate the definition.
     * @param idleTimeout the configured idle timeout that has just elapsed. Observers can use this to differentiate
     *                    between plans with different thresholds.
     */
    void onIdleTimeout(CommandDefinition command, Duration idleTimeout);
}
