package com.github.ulviar.icli.engine.runtime.internal.launch;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.TerminalPreference;

/** Chooses between pipe and PTY launchers based on {@link TerminalPreference}. */
public final class TerminalAwareCommandLauncher implements CommandLauncher {
    private final CommandLauncher pipe;
    private final CommandLauncher pty;

    public TerminalAwareCommandLauncher(CommandLauncher pipe, CommandLauncher pty) {
        this.pipe = pipe;
        this.pty = pty;
    }

    @Override
    public LaunchedProcess launch(CommandDefinition spec, boolean redirectErrorStream) {
        TerminalPreference preference = spec.terminalPreference();
        return switch (preference) {
            case DISABLED, AUTO -> pipe.launch(spec, redirectErrorStream);
            case REQUIRED -> pty.launch(spec, redirectErrorStream);
        };
    }
}
