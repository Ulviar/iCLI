package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ProcessEngine;
import com.github.ulviar.icli.core.TerminalPreference;

/**
 * Package-private helper that prepares and launches interactive sessions while honouring PTY fallbacks.
 *
 * <p>Callers provide an assembled {@link CommandCall}; the starter upgrades {@link TerminalPreference#AUTO} requests to
 * {@link TerminalPreference#REQUIRED}, attempts to start the session, and gracefully falls back to pipe mode when the
 * runtime signals unsupported PTY transport.
 */
final class InteractiveSessionStarter {

    private final ProcessEngine engine;

    InteractiveSessionStarter(ProcessEngine engine) {
        this.engine = engine;
    }

    InteractiveSessionClient start(CommandCall original) {
        boolean autoPreferredPty = original.command().terminalPreference() == TerminalPreference.AUTO;
        CommandCall call = autoPreferredPty ? upgradeToRequiredPty(original) : original;
        try {
            return createInteractiveSession(call);
        } catch (UnsupportedOperationException ex) {
            if (!autoPreferredPty) {
                throw ex;
            }
            CommandDefinition fallbackDefinition = call.command()
                    .derive()
                    .terminalPreference(TerminalPreference.DISABLED)
                    .build();
            CommandCall fallbackCall = new CommandCall(fallbackDefinition, call.options(), call.decoder());
            return createInteractiveSession(fallbackCall);
        }
    }

    private CommandCall upgradeToRequiredPty(CommandCall original) {
        CommandDefinition updated = original.command()
                .derive()
                .terminalPreference(TerminalPreference.REQUIRED)
                .build();
        return new CommandCall(updated, original.options(), original.decoder());
    }

    private InteractiveSessionClient createInteractiveSession(CommandCall call) {
        return InteractiveSessionClient.wrap(engine.startSession(call.command(), call.options()));
    }
}
