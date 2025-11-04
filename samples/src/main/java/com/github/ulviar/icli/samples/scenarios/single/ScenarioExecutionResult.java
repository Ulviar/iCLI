package com.github.ulviar.icli.samples.scenarios.single;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Captures the outcome of running a scenario through a particular adapter.
 */
public record ScenarioExecutionResult(
        String executorId,
        int exitCode,
        String stdout,
        String stderr,
        Duration duration,
        boolean timedOut,
        @Nullable Throwable error) {

    public ScenarioExecutionResult {
        executorId = Objects.requireNonNull(executorId, "executorId");
        stdout = Objects.requireNonNull(stdout, "stdout");
        stderr = Objects.requireNonNull(stderr, "stderr");
        duration = Objects.requireNonNull(duration, "duration");
    }

    public static Builder builder(String executorId) {
        return new Builder(executorId);
    }

    public boolean success() {
        return !timedOut && error == null && exitCode == 0;
    }

    public static ScenarioExecutionResult failure(String executorId, Duration duration, Throwable error) {
        return builder(executorId).exitCode(-1).duration(duration).error(error).build();
    }

    public static final class Builder {
        private final String executorId;
        private int exitCode;
        private String stdout = "";
        private String stderr = "";
        private Duration duration = Duration.ZERO;
        private boolean timedOut;
        private @Nullable Throwable error;

        private Builder(String executorId) {
            this.executorId = executorId;
        }

        public Builder exitCode(int value) {
            this.exitCode = value;
            return this;
        }

        public Builder stdout(String value) {
            this.stdout = value;
            return this;
        }

        public Builder stderr(String value) {
            this.stderr = value;
            return this;
        }

        public Builder duration(Duration value) {
            this.duration = value;
            return this;
        }

        public Builder timedOut(boolean value) {
            this.timedOut = value;
            return this;
        }

        public Builder error(@Nullable Throwable value) {
            this.error = value;
            return this;
        }

        public ScenarioExecutionResult build() {
            return new ScenarioExecutionResult(executorId, exitCode, stdout, stderr, duration, timedOut, error);
        }
    }
}
