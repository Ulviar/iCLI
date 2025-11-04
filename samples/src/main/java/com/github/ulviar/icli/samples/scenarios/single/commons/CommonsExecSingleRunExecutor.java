package com.github.ulviar.icli.samples.scenarios.single.commons;

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

/** Adapter backed by Apache Commons Exec. */
public final class CommonsExecSingleRunExecutor implements SingleRunExecutor {

    @Override
    public String id() {
        return "commons-exec";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecuteWatchdog watchdog =
                ExecuteWatchdog.builder().setTimeout(invocation.timeout()).get();
        DefaultExecutor.Builder<?> executorBuilder =
                DefaultExecutor.builder().setExecuteStreamHandler(new PumpStreamHandler(stdout, stderr));
        invocation.workingDirectory().ifPresent(executorBuilder::setWorkingDirectory);

        DefaultExecutor executor = executorBuilder.get();
        executor.setWatchdog(watchdog);
        executor.setExitValues(null); // capture non-zero exit values without exceptions

        CommandLine commandLine = new CommandLine(invocation.command().getFirst());
        invocation.command().stream().skip(1).forEach(commandLine::addArgument);

        int exitCode;
        try {
            exitCode = executor.execute(commandLine, new LinkedHashMap<>(invocation.environment()));
        } catch (ExecuteException ex) { // should not happen due to setExitValues(null), but stay defensive
            Duration duration = Duration.between(start, Instant.now());
            String stdoutText = stdout.toString(StandardCharsets.UTF_8);
            String stderrText = stderr.toString(StandardCharsets.UTF_8);
            return ScenarioExecutionResult.builder(id())
                    .exitCode(ex.getExitValue())
                    .stdout(stdoutText)
                    .stderr(stderrText)
                    .duration(duration)
                    .error(ex)
                    .timedOut(watchdog.killedProcess())
                    .build();
        } catch (Exception ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }

        Duration duration = Duration.between(start, Instant.now());
        String stdoutText = stdout.toString(StandardCharsets.UTF_8);
        String stderrText = stderr.toString(StandardCharsets.UTF_8);
        return ScenarioExecutionResult.builder(id())
                .exitCode(exitCode)
                .stdout(stdoutText)
                .stderr(stderrText)
                .duration(duration)
                .timedOut(watchdog.killedProcess())
                .build();
    }
}
