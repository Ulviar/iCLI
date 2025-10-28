package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import java.util.List;

/** Immutable snapshot of a command invocation assembled by {@link CommandCallBuilder}. */
public record CommandCall(CommandDefinition command, ExecutionOptions options, ResponseDecoder decoder) {

    /**
     * Renders the command line into a single string suitable for logs or exception messages.
     *
     * @return space-joined command arguments or {@code "<empty command>"} when no argv is present
     */
    public String renderCommandLine() {
        List<String> argv = command.command();
        return argv.isEmpty() ? "<empty command>" : String.join(" ", argv);
    }
}
