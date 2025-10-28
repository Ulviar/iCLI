package com.github.ulviar.icli.core.runtime;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.InteractiveSession;
import com.github.ulviar.icli.core.ProcessEngine;
import com.github.ulviar.icli.core.ProcessResult;
import com.github.ulviar.icli.core.runtime.diagnostics.StreamType;
import com.github.ulviar.icli.core.runtime.io.OutputSink;
import com.github.ulviar.icli.core.runtime.io.OutputSinkFactory;
import com.github.ulviar.icli.core.runtime.io.StreamDrainer;
import com.github.ulviar.icli.core.runtime.io.VirtualThreadStreamDrainer;
import com.github.ulviar.icli.core.runtime.launch.CommandLauncher;
import com.github.ulviar.icli.core.runtime.launch.PipeCommandLauncher;
import com.github.ulviar.icli.core.runtime.launch.ProcessBuilderStarter;
import com.github.ulviar.icli.core.runtime.launch.PtyCommandLauncher;
import com.github.ulviar.icli.core.runtime.launch.TerminalAwareCommandLauncher;
import com.github.ulviar.icli.core.runtime.shutdown.ShutdownExecutor;
import com.github.ulviar.icli.core.runtime.shutdown.TreeAwareProcessTerminator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Standard {@link ProcessEngine} implementation that launches commands using pipes or PTY transport based on the
 * declared {@link CommandDefinition#terminalPreference()}.
 *
 * <p>The engine provides the production defaults for the library:
 * <ul>
 *     <li>delegates launch decisions to {@link TerminalAwareCommandLauncher}, which selects between pipes and the PTY
 *     backend transparently;</li>
 *     <li>captures stdout/stderr according to the configured {@link ExecutionOptions} limits while draining both
 *     streams on virtual threads to avoid deadlocks;</li>
 *     <li>applies the configured {@link com.github.ulviar.icli.core.ShutdownPlan} when terminating commands or
 *     interactive sessions, including process-tree destruction when requested;</li>
 *     <li>exposes both single-shot execution via {@link #run(CommandDefinition, ExecutionOptions)} and interactive
 *     sessions via {@link #startSession(CommandDefinition, ExecutionOptions)}.</li>
 * </ul>
 *
 * <p>Instances are thread-safe and stateless aside from the collaborators supplied to the constructor, allowing them to
 * be reused safely across threads. A package-private constructor is provided for tests that need to inject
 * deterministic collaborators such as fixed clocks or stubbed sink factories.</p>
 *
 * <p>Operational failures encountered while supervising a launched process are surfaced as
 * {@link ProcessEngineExecutionException}. Callers should treat these as fatal for the in-flight command and follow the
 * remediation guidance documented on that exception.</p>
 *
 * @see com.github.ulviar.icli.client.CommandService
 * @see TerminalAwareCommandLauncher
 */
public final class StandardProcessEngine implements ProcessEngine {

    private final CommandLauncher launcher;
    private final OutputSinkFactory sinkFactory;
    private final StreamDrainer streamDrainer;
    private final ShutdownExecutor shutdownExecutor;
    private final Clock clock;

    public StandardProcessEngine() {
        this(
                new TerminalAwareCommandLauncher(
                        new PipeCommandLauncher(new ProcessBuilderStarter()), new PtyCommandLauncher()),
                new OutputSinkFactory(),
                new VirtualThreadStreamDrainer(),
                new ShutdownExecutor(new TreeAwareProcessTerminator()),
                Clock.systemUTC());
    }

    StandardProcessEngine(
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
     */
    @Override
    public ProcessResult run(CommandDefinition spec, ExecutionOptions options) {
        boolean redirectErrorStream = options.mergeErrorIntoOutput();
        CommandLauncher.LaunchedProcess launched = launcher.launch(spec, redirectErrorStream);
        Process process = launched.process();
        closeQuietly(process.getOutputStream());

        StreamType stdoutStream = redirectErrorStream ? StreamType.MERGED : StreamType.STDOUT;
        OutputSink stdoutSink = sinkFactory.create(options.stdoutPolicy(), stdoutStream, options.diagnosticsListener());
        OutputSink stderrSink = redirectErrorStream
                ? stdoutSink
                : sinkFactory.create(options.stderrPolicy(), StreamType.STDERR, options.diagnosticsListener());

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

    @Override
    public InteractiveSession startSession(CommandDefinition spec, ExecutionOptions options) {
        boolean redirectErrorStream = options.mergeErrorIntoOutput();
        CommandLauncher.LaunchedProcess launched = launcher.launch(spec, redirectErrorStream);
        return new ProcessInteractiveSession(
                spec,
                launched.process(),
                launched.terminalController(),
                shutdownExecutor,
                options.shutdownPlan(),
                options.destroyProcessTree(),
                options.idleTimeout(),
                options.sessionObserver());
    }

    /**
     * Close a resource while ignoring failures.
     */
    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best effort close
        }
    }

    /**
     * Waits for the child process to exit, propagating interruptions as {@link ProcessEngineExecutionException}
     * instances so callers can react consistently to supervisory failures.
     */
    private static int waitForExit(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProcessEngineExecutionException("Interrupted while waiting for process exit", ex);
        }
    }

    /**
     * Blocks until a stdout/stderr pump finishes transferring all remaining data, wrapping underlying failures in
     * {@link ProcessEngineExecutionException}.
     */
    private static void waitForPump(CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (RuntimeException ex) {
            throw new ProcessEngineExecutionException("Failed to drain process output", ex);
        }
    }
}
