package com.github.ulviar.icli.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable description of how to launch a command. */
public record CommandSpec(
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        PtyPreference ptyPreference,
        ShellSpec shell) {

    public CommandSpec {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        List<String> immutableCommand = List.copyOf(command);
        immutableCommand.forEach(arg -> Objects.requireNonNull(arg, "command must not contain null entries"));
        command = immutableCommand;

        if (environment == null || environment.isEmpty()) {
            environment = Map.of();
        } else {
            environment = Map.copyOf(environment);
        }

        ptyPreference = Objects.requireNonNullElse(ptyPreference, PtyPreference.AUTO);
        shell = Objects.requireNonNullElse(shell, ShellSpec.none());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CommandSpec of(List<String> command) {
        return builder().command(command).build();
    }

    public static final class Builder {
        private List<String> command = List.of();
        private Path workingDirectory;
        private Map<String, String> environment = Map.of();
        private PtyPreference ptyPreference = PtyPreference.AUTO;
        private ShellSpec shell = ShellSpec.none();

        private Builder() {}

        public Builder command(List<String> value) {
            Objects.requireNonNull(value, "command");
            this.command = new ArrayList<>(value);
            return this;
        }

        public Builder command(String... argv) {
            Objects.requireNonNull(argv, "argv");
            this.command = List.of(argv.clone());
            return this;
        }

        public Builder workingDirectory(Path value) {
            this.workingDirectory = value;
            return this;
        }

        public Builder environment(Map<String, String> value) {
            Objects.requireNonNull(value, "environment");
            this.environment = new LinkedHashMap<>(value);
            return this;
        }

        public Builder putEnvironment(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (environment.isEmpty()) {
                environment = new LinkedHashMap<>();
            }
            environment.put(key, value);
            return this;
        }

        public Builder ptyPreference(PtyPreference value) {
            this.ptyPreference = Objects.requireNonNull(value, "ptyPreference");
            return this;
        }

        public Builder shell(ShellSpec value) {
            this.shell = Objects.requireNonNull(value, "shell");
            return this;
        }

        public CommandSpec build() {
            Map<String, String> envCopy = environment.isEmpty() ? Map.of() : new LinkedHashMap<>(environment);
            return new CommandSpec(command, workingDirectory, envCopy, ptyPreference, shell);
        }
    }
}
