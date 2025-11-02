package com.github.ulviar.icli.engine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Immutable description of how to launch a command. */
public record CommandDefinition(
        List<String> command,
        @Nullable Path workingDirectory,
        Map<String, String> environment,
        TerminalPreference terminalPreference,
        ShellConfiguration shell) {

    public CommandDefinition {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CommandDefinition of(List<String> command) {
        return builder().command(command).build();
    }

    public Builder derive() {
        return new Builder(this);
    }

    public static final class Builder {
        private List<String> command = List.of();
        private @Nullable Path workingDirectory;
        private Map<String, String> environment = Map.of();
        private TerminalPreference terminalPreference = TerminalPreference.AUTO;
        private ShellConfiguration shell = ShellConfiguration.none();

        private Builder() {}

        private Builder(CommandDefinition template) {
            this.command = template.command;
            this.workingDirectory = template.workingDirectory;
            this.environment = new LinkedHashMap<>(template.environment);
            this.terminalPreference = template.terminalPreference;
            this.shell = template.shell;
        }

        public Builder command(List<String> value) {
            this.command = List.copyOf(value);
            return this;
        }

        public Builder command(String... argv) {
            this.command = List.of(argv);
            return this;
        }

        public Builder workingDirectory(@Nullable Path value) {
            this.workingDirectory = value;
            return this;
        }

        public Builder environment(Map<String, String> value) {
            this.environment = new LinkedHashMap<>(value);
            return this;
        }

        public Builder putEnvironment(String key, String value) {
            if (environment.isEmpty()) {
                environment = new LinkedHashMap<>();
            }
            environment.put(key, value);
            return this;
        }

        public Builder terminalPreference(TerminalPreference value) {
            this.terminalPreference = value;
            return this;
        }

        public Builder shell(ShellConfiguration value) {
            this.shell = value;
            return this;
        }

        public CommandDefinition build() {
            return new CommandDefinition(command, workingDirectory, environment, terminalPreference, shell);
        }
    }
}
