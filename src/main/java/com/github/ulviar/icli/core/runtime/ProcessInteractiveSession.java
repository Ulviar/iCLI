package com.github.ulviar.icli.core.runtime;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.InteractiveSession;
import com.github.ulviar.icli.core.SessionLifecycleObserver;
import com.github.ulviar.icli.core.ShutdownPlan;
import com.github.ulviar.icli.core.ShutdownSignal;
import com.github.ulviar.icli.core.runtime.shutdown.ShutdownExecutor;
import com.github.ulviar.icli.core.runtime.terminal.TerminalController;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Default {@link InteractiveSession} backed by a live {@link Process}. */
final class ProcessInteractiveSession implements InteractiveSession {

    private final CommandDefinition command;
    private final Process process;
    private final TerminalController terminalController;
    private final ShutdownExecutor shutdownExecutor;
    private final ShutdownPlan shutdownPlan;
    private final boolean destroyProcessTree;
    private final Duration idleTimeout;
    private final SessionLifecycleObserver sessionObserver;
    private final IdleTimeoutScheduler idleScheduler;
    private final CompletableFuture<Integer> exitFuture;
    private final OutputStream stdin;
    private final AtomicBoolean stdinClosed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ProcessInteractiveSession(
            CommandDefinition command,
            Process process,
            TerminalController terminalController,
            ShutdownExecutor shutdownExecutor,
            ShutdownPlan shutdownPlan,
            boolean destroyProcessTree,
            Duration idleTimeout,
            SessionLifecycleObserver sessionObserver) {
        this(
                command,
                process,
                terminalController,
                shutdownExecutor,
                shutdownPlan,
                destroyProcessTree,
                idleTimeout,
                sessionObserver,
                IdleTimeoutScheduler::create);
    }

    ProcessInteractiveSession(
            CommandDefinition command,
            Process process,
            TerminalController terminalController,
            ShutdownExecutor shutdownExecutor,
            ShutdownPlan shutdownPlan,
            boolean destroyProcessTree,
            Duration idleTimeout,
            SessionLifecycleObserver sessionObserver,
            IdleTimeoutSchedulerFactory idleSchedulerFactory) {
        this.command = command;
        this.process = process;
        this.terminalController = terminalController;
        this.shutdownExecutor = shutdownExecutor;
        this.shutdownPlan = shutdownPlan;
        this.destroyProcessTree = destroyProcessTree;
        this.idleTimeout = idleTimeout;
        this.sessionObserver = sessionObserver;
        this.stdin = new IdleAwareOutputStream(process.getOutputStream(), this::scheduleIdleTimeout);
        this.idleScheduler = idleSchedulerFactory.create(idleTimeout, this::handleIdleTimeout);
        this.idleScheduler.reschedule();
        this.exitFuture = process.onExit().thenApply(Process::exitValue);
        this.exitFuture.whenComplete((ignored, throwable) -> idleScheduler.close());
    }

    @Override
    public OutputStream stdin() {
        return stdin;
    }

    @Override
    public InputStream stdout() {
        return process.getInputStream();
    }

    @Override
    public InputStream stderr() {
        return process.getErrorStream();
    }

    @Override
    public CompletableFuture<Integer> onExit() {
        return exitFuture;
    }

    @Override
    public void closeStdin() {
        if (stdinClosed.compareAndSet(false, true)) {
            try {
                stdin.close();
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to close stdin", ex);
            }
        }
    }

    @Override
    public void sendSignal(ShutdownSignal signal) {
        terminalController.send(signal);
    }

    @Override
    public void resizePty(int columns, int rows) {
        terminalController.resize(columns, rows);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeStdin();
            idleScheduler.close();
            shutdownExecutor.awaitCompletion(process, shutdownPlan, destroyProcessTree);
        }
    }

    private void scheduleIdleTimeout() {
        if (closed.get()) {
            return;
        }
        idleScheduler.reschedule();
    }

    private void handleIdleTimeout() {
        if (closed.get()) {
            return;
        }
        sessionObserver.onIdleTimeout(command, idleTimeout);
        close();
    }

    interface IdleTimeoutSchedulerFactory {
        IdleTimeoutScheduler create(Duration timeout, Runnable callback);
    }
}
