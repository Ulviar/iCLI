package com.github.ulviar.icli.client.internal.runner;

import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.InteractiveSessionClient;

/**
 * Strategy interface used by client runners to turn a fully resolved {@link CommandCall} into a live
 * {@link InteractiveSessionClient}.
 *
 * <p>Implementations typically delegate to {@code ProcessEngine.startSession(...)} and may apply additional policies
 * such as PTY preference upgrades or fallbacks. Launchers must honour the {@link CommandCall#command()} metadata and
 * surface any runtime failures so callers can diagnose launch issues.</p>
 */
@FunctionalInterface
public interface SessionLauncher {

    /**
     * Launches an interactive session for the provided command call.
     *
     * @param call fully constructed command call including command definition, execution options, and decoder
     * @return interactive session client wrapping the launched process
     * @throws UnsupportedOperationException when the requested terminal preference cannot be satisfied by the runtime
     * @throws RuntimeException when process start-up fails for other reasons (propagated unchanged from the underlying
     *     engine)
     */
    InteractiveSessionClient launch(CommandCall call);
}
