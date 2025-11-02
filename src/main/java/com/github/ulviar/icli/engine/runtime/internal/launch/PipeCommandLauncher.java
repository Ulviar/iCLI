package com.github.ulviar.icli.engine.runtime.internal.launch;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.TerminalPreference;
import com.github.ulviar.icli.engine.runtime.internal.terminal.TerminalController;
import java.util.List;

/**
 * Launches commands using plain pipes via {@link ProcessBuilder} semantics. Respects {@link TerminalPreference} and
 * shell wrapping expressed in {@link CommandDefinition}, but intentionally refuses PTY-only requests until the PTY path
 * is implemented.
 */
public final class PipeCommandLauncher implements CommandLauncher {

    private final ProcessStarter starter;

    public PipeCommandLauncher(ProcessStarter starter) {
        this.starter = starter;
    }

    @Override
    public LaunchedProcess launch(CommandDefinition spec, boolean redirectErrorStream) {
        if (spec.terminalPreference() == TerminalPreference.REQUIRED) {
            throw new UnsupportedOperationException("PTY execution is not available yet.");
        }
        List<String> commandLine = CommandLineBuilder.compose(spec);
        Process process = starter.start(commandLine, spec.workingDirectory(), spec.environment(), redirectErrorStream);
        return new LaunchedProcess(process, commandLine, TerminalController.NO_OP);
    }
}
