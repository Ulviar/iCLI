package com.github.ulviar.icli.samples.scenarios.single;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of an external command invocation shared by all single-run adapters.
 */
public record CommandInvocation(
        List<String> command,
        Optional<Path> workingDirectory,
        Map<String, String> environment,
        Duration timeout,
        boolean mergeErrorIntoOutput) {

    public CommandInvocation {
        List<String> copy = List.copyOf(command);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command = copy;
        workingDirectory = Objects.requireNonNullElse(workingDirectory, Optional.empty());
        environment = Map.copyOf(environment);
        timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> command = List.of();
        private Optional<Path> workingDirectory = Optional.empty();
        private Map<String, String> environment = Map.of();
        private Duration timeout = Duration.ofSeconds(30);
        private boolean mergeErrorIntoOutput;

        private Builder() {}

        public Builder command(List<String> value) {
            this.command = List.copyOf(value);
            return this;
        }

        public Builder command(String... argv) {
            this.command = List.of(argv);
            return this;
        }

        public Builder workingDirectory(Path value) {
            this.workingDirectory = Optional.of(value);
            return this;
        }

        public Builder clearWorkingDirectory() {
            this.workingDirectory = Optional.empty();
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

        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        public Builder mergeErrorIntoOutput(boolean value) {
            this.mergeErrorIntoOutput = value;
            return this;
        }

        public CommandInvocation build() {
            return new CommandInvocation(command, workingDirectory, environment, timeout, mergeErrorIntoOutput);
        }
    }
}
