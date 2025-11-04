package com.github.ulviar.icli.samples.scenarios.single;

/** Executes a single-run scenario using a specific process library. */
public interface SingleRunExecutor {

    /** Identifier used in docs/tests (e.g., "icli", "commons-exec"). */
    String id();

    /** Runs the supplied invocation and returns a captured result. */
    ScenarioExecutionResult execute(CommandInvocation invocation);
}
