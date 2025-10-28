package com.github.ulviar.icli.core.runtime.launch;

import com.github.ulviar.icli.core.CommandDefinition;
import java.util.ArrayList;
import java.util.List;

/** Utility that merges shell wrappers with the base command line. */
final class CommandLineBuilder {

    private CommandLineBuilder() {}

    static List<String> compose(CommandDefinition spec) {
        List<String> shell = spec.shell().command();
        List<String> command = spec.command();
        List<String> combined = new ArrayList<>(shell.size() + command.size());
        combined.addAll(shell);
        combined.addAll(command);
        return combined;
    }
}
