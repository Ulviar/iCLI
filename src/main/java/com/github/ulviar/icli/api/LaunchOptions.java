package com.github.ulviar.icli.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Execution-time configuration shared across single-run and session workflows.
 */
public record LaunchOptions(
        OutputCapturePolicy stdoutPolicy,
        OutputCapturePolicy stderrPolicy,
        boolean mergeErrorIntoOutput,
        TerminationPolicy terminationPolicy,
        boolean destroyProcessTree) {

    public LaunchOptions {
        stdoutPolicy = Objects.requireNonNull(stdoutPolicy, "stdoutPolicy");
        stderrPolicy = Objects.requireNonNull(stderrPolicy, "stderrPolicy");
        terminationPolicy = Objects.requireNonNull(terminationPolicy, "terminationPolicy");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link LaunchOptions}.
     */
    public static final class Builder {

        private OutputCapturePolicy stdoutPolicy = OutputCapturePolicy.bounded(64 * 1024);
        private OutputCapturePolicy stderrPolicy = OutputCapturePolicy.bounded(32 * 1024);
        private boolean mergeErrorIntoOutput;
        private TerminationPolicy terminationPolicy =
                new TerminationPolicy(Duration.ofSeconds(60), Duration.ofSeconds(5), TerminationSignal.INTERRUPT);
        private boolean destroyProcessTree = true;

        private Builder() {}

        public Builder stdoutPolicy(OutputCapturePolicy policy) {
            this.stdoutPolicy = Objects.requireNonNull(policy, "stdoutPolicy");
            return this;
        }

        public Builder stderrPolicy(OutputCapturePolicy policy) {
            this.stderrPolicy = Objects.requireNonNull(policy, "stderrPolicy");
            return this;
        }

        public Builder mergeErrorIntoOutput(boolean value) {
            this.mergeErrorIntoOutput = value;
            return this;
        }

        public Builder terminationPolicy(TerminationPolicy policy) {
            this.terminationPolicy = Objects.requireNonNull(policy, "terminationPolicy");
            return this;
        }

        public Builder destroyProcessTree(boolean value) {
            this.destroyProcessTree = value;
            return this;
        }

        public LaunchOptions build() {
            return new LaunchOptions(
                    stdoutPolicy, stderrPolicy, mergeErrorIntoOutput, terminationPolicy, destroyProcessTree);
        }
    }
}
