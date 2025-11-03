package com.github.ulviar.icli.client;

import com.github.ulviar.icli.client.internal.runner.SessionLauncher;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.TerminalPreference;
import java.util.List;

/**
 * Package-private helper that prepares and launches interactive sessions while honouring PTY fallbacks.
 *
 * <p>Callers provide an assembled {@link CommandCall}; the starter upgrades {@link TerminalPreference#AUTO} requests to
 * {@link TerminalPreference#REQUIRED}, attempts to start the session, and gracefully falls back to pipe mode when the
 * runtime signals unsupported PTY transport.
 */
final class InteractiveSessionStarter implements SessionLauncher {

    private final ProcessEngine engine;

    /**
     * Creates a starter that delegates session launches to the provided process engine.
     *
     * @param engine process engine responsible for starting interactive sessions
     */
    InteractiveSessionStarter(ProcessEngine engine) {
        this.engine = engine;
    }

    @Override
    public InteractiveSessionClient launch(CommandCall call) {
        return start(call);
    }

    /**
     * Launches the session while honouring automatic PTY fallbacks.
     *
     * <p>When the command's terminal preference is {@link TerminalPreference#AUTO}, the starter first attempts to
     * launch with {@link TerminalPreference#REQUIRED}. If the runtime reports that PTY transport is unavailable, the
     * call is retried with {@link TerminalPreference#DISABLED} before surfacing the failure.</p>
     *
     * @param original command call provided by the runner
     * @return interactive session client wrapping the launched process
     * @throws UnsupportedOperationException when the caller explicitly required a PTY and the runtime cannot supply it
     */
    private InteractiveSessionClient start(CommandCall original) {
        List<CommandCall> attempts = buildLaunchAttempts(original);
        UnsupportedOperationException lastUnsupported = null;
        for (CommandCall attempt : attempts) {
            try {
                return createInteractiveSession(attempt);
            } catch (UnsupportedOperationException ex) {
                lastUnsupported = ex;
            }
        }
        if (lastUnsupported != null) {
            throw lastUnsupported;
        }
        throw new UnsupportedOperationException("Unable to launch interactive session for preference "
                + original.command().terminalPreference());
    }

    private List<CommandCall> buildLaunchAttempts(CommandCall original) {
        if (original.command().terminalPreference() == TerminalPreference.AUTO) {
            return List.of(
                    withTerminalPreference(original, TerminalPreference.REQUIRED),
                    withTerminalPreference(original, TerminalPreference.DISABLED));
        }
        return List.of(original);
    }

    /**
     * Delegates to the process engine to start a session for the supplied call.
     *
     * @param call command call describing the session to launch
     * @return interactive session client wrapping the launched process
     * @throws RuntimeException when the engine cannot create the session (propagated unchanged)
     */
    private InteractiveSessionClient createInteractiveSession(CommandCall call) {
        return InteractiveSessionClient.wrap(engine.startSession(call.command(), call.options()));
    }

    private CommandCall withTerminalPreference(CommandCall original, TerminalPreference preference) {
        CommandDefinition updated =
                original.command().derive().terminalPreference(preference).build();
        return new CommandCall(updated, original.options(), original.decoder());
    }
}
