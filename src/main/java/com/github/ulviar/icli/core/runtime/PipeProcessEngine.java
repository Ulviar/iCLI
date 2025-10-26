package com.github.ulviar.icli.core.runtime;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.InteractiveSession;
import com.github.ulviar.icli.core.ProcessEngine;
import com.github.ulviar.icli.core.ProcessResult;
import com.github.ulviar.icli.core.TerminalPreference;
import com.github.ulviar.icli.core.runtime.io.OutputSink;
import com.github.ulviar.icli.core.runtime.io.OutputSinkFactory;
import com.github.ulviar.icli.core.runtime.io.StreamDrainer;
import com.github.ulviar.icli.core.runtime.io.VirtualThreadStreamDrainer;
import com.github.ulviar.icli.core.runtime.launch.CommandLauncher;
import com.github.ulviar.icli.core.runtime.launch.PipeCommandLauncher;
import com.github.ulviar.icli.core.runtime.launch.ProcessBuilderStarter;
import com.github.ulviar.icli.core.runtime.shutdown.ShutdownExecutor;
import com.github.ulviar.icli.core.runtime.shutdown.TreeAwareProcessTerminator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ProcessEngine} implementation that executes commands via plain pipes while honouring all
 * {@link ExecutionOptions} policies (capture limits, shutdown plans, and merge behaviour).
 */
public final class PipeProcessEngine implements ProcessEngine {

    private final CommandLauncher launcher;
    private final OutputSinkFactory sinkFactory;
    private final StreamDrainer streamDrainer;
    private final ShutdownExecutor shutdownExecutor;
    private final Clock clock;

    public PipeProcessEngine() {
        this(
                new PipeCommandLauncher(new ProcessBuilderStarter()),
                new OutputSinkFactory(),
                new VirtualThreadStreamDrainer(),
                new ShutdownExecutor(new TreeAwareProcessTerminator()),
                Clock.systemUTC());
    }

    private PipeProcessEngine(
            CommandLauncher launcher,
            OutputSinkFactory sinkFactory,
            StreamDrainer streamDrainer,
            ShutdownExecutor shutdownExecutor,
            Clock clock) {
        this.launcher = launcher;
        this.sinkFactory = sinkFactory;
        this.streamDrainer = streamDrainer;
        this.shutdownExecutor = shutdownExecutor;
        this.clock = clock;
    }

    /**
     * Execute {@code spec} with the supplied options, returning the captured result.
     *
     * @throws UnsupportedOperationException if {@link TerminalPreference#REQUIRED} is requested
     */
    @Override
    public ProcessResult run(CommandDefinition spec, ExecutionOptions options) {
        boolean redirectErrorStream = options.mergeErrorIntoOutput();
        CommandLauncher.LaunchedProcess launched = launcher.launch(spec, redirectErrorStream);
        Process process = launched.process();
        closeQuietly(process.getOutputStream());

        OutputSink stdoutSink = sinkFactory.create(options.stdoutPolicy());
        OutputSink stderrSink = redirectErrorStream ? stdoutSink : sinkFactory.create(options.stderrPolicy());

        CompletableFuture<Void> stdoutPump = streamDrainer.drain(process.getInputStream(), stdoutSink);
        CompletableFuture<Void> stderrPump = redirectErrorStream
                ? CompletableFuture.completedFuture(null)
                : streamDrainer.drain(process.getErrorStream(), stderrSink);

        Instant start = clock.instant();
        shutdownExecutor.awaitCompletion(process, options.shutdownPlan(), options.destroyProcessTree());
        int exitCode = waitForExit(process);
        waitForPump(stdoutPump);
        if (!redirectErrorStream) {
            waitForPump(stderrPump);
        }

        Duration duration = Duration.between(start, clock.instant());
        String stdout = stdoutSink.content();
        String stderr = redirectErrorStream ? "" : stderrSink.content();

        return new ProcessResult(exitCode, stdout, stderr, Optional.of(duration));
    }

    /** This implementation exposes only single-run behaviour; interactive sessions are not supported. */
    @Override
    public InteractiveSession startSession(CommandDefinition spec, ExecutionOptions options) {
        throw new UnsupportedOperationException("Interactive sessions are not implemented yet.");
    }

    /** Close a resource while ignoring failures. */
    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best effort close
        }
    }

    /** Wait for the child process to exit, rethrowing interruptions as runtime failures. */
    private static int waitForExit(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for process exit", ex);
        }
    }

    /** Block until a stdout/stderr pump finishes transferring all remaining data. */
    private static void waitForPump(CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (RuntimeException ex) {
            throw new RuntimeException("Failed to drain process output", ex);
        }
    }
}
