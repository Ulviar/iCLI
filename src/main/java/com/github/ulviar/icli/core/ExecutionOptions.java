package com.github.ulviar.icli.core;

import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsListener;
import java.time.Duration;

/**
 * Defines how the runtime supervises a launched process once the command line has been constructed.
 *
 * <p>Instances are immutable, thread-safe, and may be reused across multiple command invocations. Use
 * {@link #builder()} to construct a customised configuration or {@link #derive()} to tweak an existing instance while
 * preserving its other values.
 *
 * <p>Unless overridden, the runtime applies the following defaults:
 * <ul>
 *     <li>stdout capture bounded to 64 KiB;</li>
 *     <li>stderr capture bounded to 32 KiB;</li>
 *     <li>stdout and stderr kept separate (no merge);</li>
 *     <li>a graceful interrupt after 60 seconds followed by a 5 second hard-kill grace period;</li>
 *     <li>descendant process termination enabled;</li>
 *     <li>a five minute idle timeout for interactive sessions;</li>
 *     <li>{@link SessionLifecycleObserver#NO_OP} for lifecycle notifications.</li>
 *     <li>{@link DiagnosticsListener#noOp()} for diagnostics output events.</li>
 * </ul>
 *
 * @param stdoutPolicy         output capture strategy applied to stdout. Defaults to bounded text capture up to 64 KiB
 *                             while keeping raw bytes available to diagnostics.
 * @param stderrPolicy         output capture strategy applied to stderr. Mirrors {@code stdoutPolicy} but defaults to a
 *                             32 KiB limit because most tooling emits less diagnostic output.
 * @param mergeErrorIntoOutput whether stderr should be merged into stdout. When {@code true} the stderr policy still
 *                             governs truncation, but callers observe a single combined stream.
 * @param shutdownPlan         escalation strategy used when the process needs to be terminated. By default the runtime
 *                             sends a soft interrupt after 60 seconds and escalates to a hard kill after a 5 second
 *                             grace period.
 * @param destroyProcessTree   whether the entire descendant process tree should be terminated alongside the direct
 *                             child. Keeping this enabled prevents orphaned helper processes.
 * @param idleTimeout          idle timeout for interactive sessions. Values less than or equal to zero disable idle
 *                             tracking. The default is five minutes.
 * @param sessionObserver      lifecycle observer invoked when the session is about to terminate (for example, because
 *                             the idle timeout elapsed). Defaults to {@link SessionLifecycleObserver#NO_OP}.
 * @param diagnosticsListener  listener notified about streaming output and truncation events. Defaults to
 *                             {@link DiagnosticsListener#noOp()}.
 */
public record ExecutionOptions(
        OutputCapture stdoutPolicy,
        OutputCapture stderrPolicy,
        boolean mergeErrorIntoOutput,
        ShutdownPlan shutdownPlan,
        boolean destroyProcessTree,
        Duration idleTimeout,
        SessionLifecycleObserver sessionObserver,
        DiagnosticsListener diagnosticsListener) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder derive() {
        return Builder.from(this);
    }

    /**
     * Fluent builder that exposes the default options used by {@link ExecutionOptions}. Builders are mutable and NOT
     * thread-safe; create a distinct instance per call site and call {@link #build()} once configuration is complete.
     */
    public static final class Builder {

        private OutputCapture stdoutPolicy = OutputCapture.bounded(64 * 1024);
        private OutputCapture stderrPolicy = OutputCapture.bounded(32 * 1024);
        private boolean mergeErrorIntoOutput;
        private ShutdownPlan shutdownPlan =
                new ShutdownPlan(Duration.ofSeconds(60), Duration.ofSeconds(5), ShutdownSignal.INTERRUPT);
        private boolean destroyProcessTree = true;
        private Duration idleTimeout = Duration.ofMinutes(5);
        private SessionLifecycleObserver sessionObserver = SessionLifecycleObserver.NO_OP;
        private DiagnosticsListener diagnosticsListener = DiagnosticsListener.noOp();

        private Builder() {}

        private Builder(ExecutionOptions template) {
            this.stdoutPolicy = template.stdoutPolicy;
            this.stderrPolicy = template.stderrPolicy;
            this.mergeErrorIntoOutput = template.mergeErrorIntoOutput;
            this.shutdownPlan = template.shutdownPlan;
            this.destroyProcessTree = template.destroyProcessTree;
            this.idleTimeout = template.idleTimeout;
            this.sessionObserver = template.sessionObserver;
            this.diagnosticsListener = template.diagnosticsListener;
        }

        static Builder from(ExecutionOptions template) {
            return new Builder(template);
        }

        /**
         * Overrides the {@code stdout} capture policy. Callers may supply built-in strategies (bounded, discard,
         * streaming) or custom implementations; the supplied instance is used as-is without defensive copying.
         */
        public Builder stdoutPolicy(OutputCapture policy) {
            this.stdoutPolicy = policy;
            return this;
        }

        /**
         * Overrides the {@code stderr} capture policy. Behaviour matches {@link #stdoutPolicy(OutputCapture)} and is
         * applied regardless of {@link #mergeErrorIntoOutput(boolean)} - merging only affects how results are exposed
         * to the client.
         */
        public Builder stderrPolicy(OutputCapture policy) {
            this.stderrPolicy = policy;
            return this;
        }

        /**
         * Configures whether {@code stderr} should be appended to {@code stdout}. When {@code true}, the stderr policy
         * still governs truncation but the merged stream is returned through {@code stdout}.
         */
        public Builder mergeErrorIntoOutput(boolean value) {
            this.mergeErrorIntoOutput = value;
            return this;
        }

        /**
         * Replaces the default shutdown escalation. Supply a {@link ShutdownPlan} tuned to the command's expected
         * runtime behaviour (e.g., longer grace periods for debuggers or tighter budgets for short-lived utilities).
         */
        public Builder shutdownPlan(ShutdownPlan plan) {
            this.shutdownPlan = plan;
            return this;
        }

        /**
         * Enables or disables recursive termination. Set to {@code false} only when downstream tooling intentionally
         * spawns long-lived helpers that must survive the parent process.
         */
        public Builder destroyProcessTree(boolean value) {
            this.destroyProcessTree = value;
            return this;
        }

        /**
         * Adjusts the session idle timeout. Provide {@link Duration#ZERO} or a negative value to disable automatic idle
         * shutdown entirely.
         */
        public Builder idleTimeout(Duration value) {
            this.idleTimeout = value;
            return this;
        }

        /**
         * Sets a callback to observe session lifecycle events. The observer is invoked synchronously on the runtime
         * thread that detects the event - avoid blocking or long-running work inside the callback.
         */
        public Builder sessionObserver(SessionLifecycleObserver value) {
            this.sessionObserver = value;
            return this;
        }

        /**
         * Sets the diagnostics listener notified about streaming chunks and truncation events.
         *
         * <p>Events are dispatched synchronously on the stdout/stderr draining threads; listeners must avoid blocking
         * or lengthy work to prevent back-pressure on the child process.
         */
        public Builder diagnosticsListener(DiagnosticsListener value) {
            this.diagnosticsListener = value;
            return this;
        }

        /**
         * Produces an immutable {@link ExecutionOptions} snapshot reflecting the current builder state. Subsequent
         * modifications to the builder do not affect previously built instances.
         */
        public ExecutionOptions build() {
            return new ExecutionOptions(
                    stdoutPolicy,
                    stderrPolicy,
                    mergeErrorIntoOutput,
                    shutdownPlan,
                    destroyProcessTree,
                    idleTimeout,
                    sessionObserver,
                    diagnosticsListener);
        }
    }
}
