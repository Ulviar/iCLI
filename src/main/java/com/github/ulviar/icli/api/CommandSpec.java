package com.github.ulviar.icli.api;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Immutable description of how to launch a command. */
public record CommandSpec(
        List<String> command,
        @Nullable Path workingDirectory,
        Map<String, String> environment,
        PtyPreference ptyPreference,
        ShellSpec shell) {

    public CommandSpec {
        command = List.copyOf(command);
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        environment = Map.copyOf(environment);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CommandSpec of(List<String> command) {
        return builder().command(command).build();
    }

    public static final class Builder {
        private List<String> command = List.of();
        private @Nullable Path workingDirectory;
        private Map<String, String> environment = Map.of();
        private PtyPreference ptyPreference = PtyPreference.AUTO;
        private ShellSpec shell = ShellSpec.none();

        private Builder() {}

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

        public Builder ptyPreference(PtyPreference value) {
            this.ptyPreference = value;
            return this;
        }

        public Builder shell(ShellSpec value) {
            this.shell = value;
            return this;
        }

        public CommandSpec build() {
            return new CommandSpec(command, workingDirectory, environment, ptyPreference, shell);
        }
    }
}
