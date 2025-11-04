package com.github.ulviar.icli.samples.scenarios.single.jline;

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import org.jline.utils.ExecHelper;

/** Adapter that reuses JLine's {@link ExecHelper} utilities for capturing output. */
public final class JLineSingleRunExecutor implements SingleRunExecutor {

    @Override
    public String id() {
        return "jline";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        ProcessBuilder builder = new ProcessBuilder(invocation.command());
        builder.environment().putAll(new LinkedHashMap<>(invocation.environment()));
        invocation.workingDirectory().ifPresent(path -> builder.directory(path.toFile()));

        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }

        Duration timeout = invocation.timeout();
        boolean finished;
        try {
            if (timeout.isZero()) {
                process.waitFor();
                finished = true;
            } else {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }

        Duration duration = Duration.between(start, Instant.now());
        if (!finished) {
            process.destroyForcibly();
            return ScenarioExecutionResult.builder(id())
                    .exitCode(-1)
                    .stdout("")
                    .stderr("")
                    .duration(duration)
                    .timedOut(true)
                    .build();
        }

        try {
            String merged = ExecHelper.waitAndCapture(process);
            int exitCode = process.exitValue();
            return ScenarioExecutionResult.builder(id())
                    .exitCode(exitCode)
                    .stdout(merged)
                    .stderr(invocation.mergeErrorIntoOutput() ? "" : merged)
                    .duration(Duration.between(start, Instant.now()))
                    .build();
        } catch (Exception ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }
    }
}
