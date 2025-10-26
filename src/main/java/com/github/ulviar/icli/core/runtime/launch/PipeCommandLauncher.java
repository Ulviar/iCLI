package com.github.ulviar.icli.core.runtime.launch;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.TerminalPreference;
import java.util.ArrayList;
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
        List<String> commandLine = buildCommandLine(spec);
        Process process = starter.start(commandLine, spec.workingDirectory(), spec.environment(), redirectErrorStream);
        return new LaunchedProcess(process, commandLine);
    }

    /**
     * Merge shell wrapper (if any) with the direct command definition.
     */
    private static List<String> buildCommandLine(CommandDefinition spec) {
        List<String> shell = spec.shell().command();
        List<String> command = spec.command();
        List<String> combined = new ArrayList<>(shell.size() + command.size());
        combined.addAll(shell);
        combined.addAll(command);
        return combined;
    }
}
