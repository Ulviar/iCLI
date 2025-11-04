package com.github.ulviar.icli.samples.scenarios.single.zte;

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/** Adapter backed by ZeroTurnaround's ProcessExecutor (zt-exec). */
public final class ZtExecSingleRunExecutor implements SingleRunExecutor {

    @Override
    public String id() {
        return "zt-exec";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ProcessExecutor executor = new ProcessExecutor(invocation.command())
                .environment(new LinkedHashMap<>(invocation.environment()))
                .redirectOutput(stdout)
                .redirectError(stderr)
                .timeout(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .exitValueAny()
                .destroyOnExit();
        invocation.workingDirectory().ifPresent(path -> executor.directory(path.toFile()));

        try {
            ProcessResult result = executor.execute();
            Duration duration = Duration.between(start, Instant.now());
            String stdoutText = stdout.toString(StandardCharsets.UTF_8);
            String stderrText = stderr.toString(StandardCharsets.UTF_8);
            return ScenarioExecutionResult.builder(id())
                    .exitCode(result.getExitValue())
                    .stdout(stdoutText)
                    .stderr(stderrText)
                    .duration(duration)
                    .build();
        } catch (TimeoutException timeout) {
            Duration duration = Duration.between(start, Instant.now());
            String stdoutText = stdout.toString(StandardCharsets.UTF_8);
            String stderrText = stderr.toString(StandardCharsets.UTF_8);
            return ScenarioExecutionResult.builder(id())
                    .exitCode(-1)
                    .stdout(stdoutText)
                    .stderr(stderrText)
                    .duration(duration)
                    .timedOut(true)
                    .error(timeout)
                    .build();
        } catch (Exception ex) {
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }
    }
}
