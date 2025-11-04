package com.github.ulviar.icli.samples.scenarios.single.nuprocess;

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation;
import com.github.ulviar.icli.samples.scenarios.single.ScenarioExecutionResult;
import com.github.ulviar.icli.samples.scenarios.single.SingleRunExecutor;
import com.zaxxer.nuprocess.NuAbstractProcessHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Adapter that executes commands via NuProcess. */
public final class NuProcessSingleRunExecutor implements SingleRunExecutor {

    @Override
    public String id() {
        return "nuprocess";
    }

    @Override
    public ScenarioExecutionResult execute(CommandInvocation invocation) {
        Instant start = Instant.now();
        CapturingHandler handler = new CapturingHandler(invocation.mergeErrorIntoOutput());
        NuProcessBuilder builder = new NuProcessBuilder(invocation.command());
        builder.environment().putAll(new LinkedHashMap<>(invocation.environment()));
        invocation.workingDirectory().ifPresent(builder::setCwd);
        builder.setProcessListener(handler);

        NuProcess process = builder.start();

        boolean finished;
        try {
            finished = handler.await(invocation.timeout());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroy(true);
            return ScenarioExecutionResult.failure(id(), Duration.between(start, Instant.now()), ex);
        }

        if (!finished) {
            process.destroy(true);
            return ScenarioExecutionResult.builder(id())
                    .exitCode(-1)
                    .stdout(handler.stdout())
                    .stderr(handler.stderr())
                    .duration(Duration.between(start, Instant.now()))
                    .timedOut(true)
                    .build();
        }

        return ScenarioExecutionResult.builder(id())
                .exitCode(handler.exitCode())
                .stdout(handler.stdout())
                .stderr(handler.stderr())
                .duration(Duration.between(start, Instant.now()))
                .build();
    }

    private static final class CapturingHandler extends NuAbstractProcessHandler {
        private final boolean merge;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final CountDownLatch exit = new CountDownLatch(1);
        private volatile int exitCode;

        private CapturingHandler(boolean merge) {
            this.merge = merge;
        }

        @Override
        public void onStdout(ByteBuffer buffer, boolean closed) {
            drain(buffer, stdout);
        }

        @Override
        public void onStderr(ByteBuffer buffer, boolean closed) {
            drain(buffer, stderr);
        }

        @Override
        public void onExit(int exitCode) {
            this.exitCode = exitCode;
            exit.countDown();
        }

        boolean await(Duration timeout) throws InterruptedException {
            if (timeout.isZero() || timeout.isNegative()) {
                exit.await();
                return true;
            }
            return exit.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        int exitCode() {
            return exitCode;
        }

        String stdout() {
            if (merge && stderr.size() > 0) {
                ByteArrayOutputStream merged = new ByteArrayOutputStream();
                try {
                    stdout.writeTo(merged);
                    stderr.writeTo(merged);
                } catch (IOException ignore) {
                    // ByteArrayOutputStream#writeTo never throws for ByteArrayOutputStream
                }
                return merged.toString(StandardCharsets.UTF_8);
            }
            return stdout.toString(StandardCharsets.UTF_8);
        }

        String stderr() {
            return merge ? "" : stderr.toString(StandardCharsets.UTF_8);
        }

        private static void drain(ByteBuffer buffer, ByteArrayOutputStream target) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            target.writeBytes(chunk);
        }
    }
}
