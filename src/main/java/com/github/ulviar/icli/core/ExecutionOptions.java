package com.github.ulviar.icli.core;

import java.time.Duration;

/** Execution-time configuration shared across single-run and session workflows. */
public record ExecutionOptions(
        OutputCapture stdoutPolicy,
        OutputCapture stderrPolicy,
        boolean mergeErrorIntoOutput,
        ShutdownPlan shutdownPlan,
        boolean destroyProcessTree) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder derive() {
        return Builder.from(this);
    }

    /** Fluent builder for {@link ExecutionOptions}. */
    public static final class Builder {

        private OutputCapture stdoutPolicy = OutputCapture.bounded(64 * 1024);
        private OutputCapture stderrPolicy = OutputCapture.bounded(32 * 1024);
        private boolean mergeErrorIntoOutput;
        private ShutdownPlan shutdownPlan =
                new ShutdownPlan(Duration.ofSeconds(60), Duration.ofSeconds(5), ShutdownSignal.INTERRUPT);
        private boolean destroyProcessTree = true;

        private Builder() {}

        private Builder(ExecutionOptions template) {
            this.stdoutPolicy = template.stdoutPolicy;
            this.stderrPolicy = template.stderrPolicy;
            this.mergeErrorIntoOutput = template.mergeErrorIntoOutput;
            this.shutdownPlan = template.shutdownPlan;
            this.destroyProcessTree = template.destroyProcessTree;
        }

        static Builder from(ExecutionOptions template) {
            return new Builder(template);
        }

        public Builder stdoutPolicy(OutputCapture policy) {
            this.stdoutPolicy = policy;
            return this;
        }

        public Builder stderrPolicy(OutputCapture policy) {
            this.stderrPolicy = policy;
            return this;
        }

        public Builder mergeErrorIntoOutput(boolean value) {
            this.mergeErrorIntoOutput = value;
            return this;
        }

        public Builder shutdownPlan(ShutdownPlan plan) {
            this.shutdownPlan = plan;
            return this;
        }

        public Builder destroyProcessTree(boolean value) {
            this.destroyProcessTree = value;
            return this;
        }

        public ExecutionOptions build() {
            return new ExecutionOptions(
                    stdoutPolicy, stderrPolicy, mergeErrorIntoOutput, shutdownPlan, destroyProcessTree);
        }
    }
}
