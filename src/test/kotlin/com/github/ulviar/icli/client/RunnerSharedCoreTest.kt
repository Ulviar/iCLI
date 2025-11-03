package com.github.ulviar.icli.client

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ProcessEngine
import com.github.ulviar.icli.engine.ProcessResult
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.TerminalPreference
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunnerSharedCoreTest {
    private val baseCommand: CommandDefinition =
        CommandDefinition.builder().command("echo", "baseline").build()
    private val defaultOptions: ExecutionOptions = ExecutionOptions.builder().build()

    @Test
    fun commandRunnerAppliesCustomisationThroughSharedFactory() {
        val engine = RecordingProcessEngine()
        val service = CommandService(engine, baseCommand, defaultOptions, DirectScheduler())

        val result =
            service.runner().run { builder ->
                builder.args("custom")
                builder.customizeOptions { options -> options.mergeErrorIntoOutput(true) }
            }

        assertTrue(result.success)
        assertEquals("ok", result.value)
        val (command, options) = engine.runInvocations.single()
        assertEquals(listOf("echo", "baseline", "custom"), command.command())
        assertTrue(options.mergeErrorIntoOutput())
    }

    @Test
    fun lineSessionRunnerPassesDerivedCommandAndOptionsToLauncher() {
        val engine = RecordingProcessEngine()
        val service = CommandService(engine, baseCommand, defaultOptions, DirectScheduler())

        val client =
            service.lineSessionRunner().open { builder ->
                builder.subcommand("status")
                builder.customizeOptions { options -> options.idleTimeout(Duration.ZERO) }
            }

        assertNotNull(client)
        val (command, options) = engine.sessionInvocations.single()
        assertEquals(listOf("echo", "baseline", "status"), command.command())
        assertEquals(Duration.ZERO, options.idleTimeout())
    }

    @Test
    fun interactiveSessionRunnerHonoursTerminalPreferenceOverride() {
        val engine = RecordingProcessEngine()
        val service = CommandService(engine, baseCommand, defaultOptions, DirectScheduler())

        service.interactiveSessionRunner().open { builder ->
            builder.terminalPreference(TerminalPreference.DISABLED)
        }

        val (command, _) = engine.sessionInvocations.single()
        assertEquals(TerminalPreference.DISABLED, command.terminalPreference())
    }

    @Test
    fun interactiveSessionRunnerFallsBackWhenAutoRequiresPipe() {
        val engine = RecordingProcessEngine()
        val service = CommandService(engine, baseCommand, defaultOptions, DirectScheduler())
        val attempts = mutableListOf<TerminalPreference>()
        engine.sessionHandler = { command, options ->
            attempts += command.terminalPreference()
            if (command.terminalPreference() == TerminalPreference.REQUIRED) {
                throw UnsupportedOperationException("pty unavailable")
            }
            StubInteractiveSession()
        }

        val client = service.interactiveSessionRunner().open()

        assertNotNull(client)
        assertEquals(
            listOf(TerminalPreference.REQUIRED, TerminalPreference.DISABLED),
            attempts,
        )
        assertEquals(
            listOf(TerminalPreference.REQUIRED, TerminalPreference.DISABLED),
            engine.sessionInvocations.map { (command, _) -> command.terminalPreference() },
        )
    }

    @Test
    fun interactiveSessionRunnerPropagatesUnsupportedWhenRequiredRequested() {
        val engine = RecordingProcessEngine()
        val service = CommandService(engine, baseCommand, defaultOptions, DirectScheduler())
        val attempts = mutableListOf<TerminalPreference>()
        engine.sessionHandler = { command, _ ->
            attempts += command.terminalPreference()
            throw UnsupportedOperationException("pty unavailable")
        }

        assertFailsWith<UnsupportedOperationException> {
            service.interactiveSessionRunner().open { builder ->
                builder.terminalPreference(TerminalPreference.REQUIRED)
            }
        }
        assertEquals(listOf(TerminalPreference.REQUIRED), attempts)
    }

    private class RecordingProcessEngine : ProcessEngine {
        val runInvocations = mutableListOf<Pair<CommandDefinition, ExecutionOptions>>()
        val sessionInvocations = mutableListOf<Pair<CommandDefinition, ExecutionOptions>>()

        var runResult: ProcessResult =
            ProcessResult(0, "ok", "", Optional.empty())
        var sessionHandler: (CommandDefinition, ExecutionOptions) -> InteractiveSession =
            { _, _ -> StubInteractiveSession() }

        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult {
            runInvocations += spec to options
            return runResult
        }

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession {
            sessionInvocations += spec to options
            return sessionHandler(spec, options)
        }
    }

    private class DirectScheduler : ClientScheduler {
        override fun <T> submit(task: Callable<T>): CompletableFuture<T> =
            try {
                CompletableFuture.completedFuture(task.call())
            } catch (ex: Exception) {
                CompletableFuture<T>().apply { completeExceptionally(ex) }
            }

        override fun close() {}
    }

    private class StubInteractiveSession : InteractiveSession {
        private val stdinStream = ByteArrayOutputStream()
        private val exitFuture = CompletableFuture.completedFuture(0)

        override fun stdin(): OutputStream = stdinStream

        override fun stdout(): InputStream = InputStream.nullInputStream()

        override fun stderr(): InputStream = InputStream.nullInputStream()

        override fun onExit(): CompletableFuture<Int> = exitFuture

        override fun closeStdin() {}

        override fun sendSignal(signal: ShutdownSignal) {}

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {}

        override fun close() {}
    }
}
