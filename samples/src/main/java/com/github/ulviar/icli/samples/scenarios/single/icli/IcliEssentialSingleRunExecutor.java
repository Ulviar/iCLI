package com.github.ulviar.icli.samples.scenarios.single.icli;

import com.github.ulviar.icli.client.CommandResult;
import com.github.ulviar.icli.client.CommandService;
import com.github.ulviar.icli.client.ProcessExecutionException;
import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.ExecutionOptions;
import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine;
import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import java.time.Duration;
import java.time.Instant;

/** Adapter showcasing the iCLI Essential API via {@link CommandService}. */
public final class IcliEssentialSingleRunExecutor implements SingleRunExecutor {

    private static final ProcessEngine ENGINE = new StandardProcessEngine();

    @Override
    public String id() {
        return "icli-essential";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        try {
            CommandDefinition.Builder builder = CommandDefinition.builder().command(invocation.command());
            invocation.workingDirectory().ifPresent(builder::workingDirectory);
            CommandDefinition definition =
                    builder.environment(invocation.environment()).build();

            ExecutionOptions options = ExecutionOptions.builder()
                    .mergeErrorIntoOutput(invocation.mergeErrorIntoOutput())
                    .shutdownPlan(IcliShutdownPlans.forTimeout(invocation.timeout()))
                    .build();

            CommandService service = new CommandService(ENGINE, definition, options);
            CommandResult<String> result = service.runner().run();
            Duration duration = Duration.between(start, Instant.now());
            if (result.success()) {
                return ScenarioExecutionResult.builder(id())
                        .exitCode(0)
                        .stdout(result.value())
                        .stderr("")
                        .duration(duration)
                        .build();
            }
            Throwable error = result.error();
            if (error instanceof ProcessExecutionException failure) {
                return ScenarioExecutionResult.builder(id())
                        .exitCode(failure.exitCode())
                        .stdout(failure.stdout())
                        .stderr(failure.stderr())
                        .duration(duration)
                        .error(failure)
                        .build();
            }
            return ScenarioExecutionResult.failure(id(), duration, error);
        } catch (RuntimeException ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }
    }
}
