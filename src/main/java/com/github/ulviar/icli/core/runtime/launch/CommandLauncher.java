package com.github.ulviar.icli.core.runtime.launch;

import com.github.ulviar.icli.core.CommandDefinition;
import java.util.List;

/**
 * Strategy abstraction for turning a {@link CommandDefinition} into a running {@link Process}. Different launchers can
 * provide pipe-only, PTY-enabled, or pooled start-up paths while keeping the rest of the runtime agnostic.
 */
public interface CommandLauncher {

    /**
     * Launch the supplied command.
     *
     * @param spec fully prepared {@link CommandDefinition}
     * @param redirectErrorStream whether stderr should be merged into stdout by the underlying process
     * @return {@link LaunchedProcess} containing the live {@link Process} and the resolved command line
     */
    LaunchedProcess launch(CommandDefinition spec, boolean redirectErrorStream);

    record LaunchedProcess(Process process, List<String> commandLine) {
        /**
         * @param process live child process handle
         * @param commandLine effective argv used to start the process (defensively copied)
         */
        public LaunchedProcess {
            commandLine = List.copyOf(commandLine);
        }
    }
}
