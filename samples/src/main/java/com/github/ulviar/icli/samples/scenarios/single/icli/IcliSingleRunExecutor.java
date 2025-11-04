package com.github.ulviar.icli.samples.scenarios.single.icli;

import com.github.ulviar.icli.client.ProcessExecutionException;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.ProcessResult;
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine;
import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import java.time.Duration;
import java.time.Instant;

/** Adapter that runs commands through the iCLI {@link StandardProcessEngine}. */
public final class IcliSingleRunExecutor implements SingleRunExecutor {

    private static final ProcessEngine ENGINE = new StandardProcessEngine();

    @Override
    public String id() {
        return "icli-advanced";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        try {
            CommandDefinition definition = CommandDefinition.builder()
                    .command(invocation.command())
                    .workingDirectory(invocation.workingDirectory().orElse(null))
                    .environment(invocation.environment())
                    .build();

            ExecutionOptions options = ExecutionOptions.builder()
                    .mergeErrorIntoOutput(invocation.mergeErrorIntoOutput())
                    .shutdownPlan(IcliShutdownPlans.forTimeout(invocation.timeout()))
                    .build();

            ProcessResult result = ENGINE.run(definition, options);
            return ScenarioExecutionResult.builder(id())
                    .exitCode(result.exitCode())
                    .stdout(result.stdout())
                    .stderr(result.stderr())
                    .duration(Duration.between(start, Instant.now()))
                    .build();
        } catch (ProcessExecutionException failure) {
            return ScenarioExecutionResult.builder(id())
                    .exitCode(failure.exitCode())
                    .stdout(failure.stdout())
                    .stderr(failure.stderr())
                    .duration(Duration.between(start, Instant.now()))
                    .error(failure)
                    .build();
        } catch (RuntimeException ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }
    }
}
