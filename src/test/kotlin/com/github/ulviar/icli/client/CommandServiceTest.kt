package com.github.ulviar.icli.client

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.OutputCapture
import com.github.ulviar.icli.engine.ProcessResult
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.testing.ImmediateClientScheduler
import com.github.ulviar.icli.testing.RecordingExecutionEngine
import com.github.ulviar.icli.testing.ScriptedInteractiveSession
import java.time.Duration
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandServiceTest {
    private lateinit var engine: RecordingExecutionEngine
    private lateinit var baseCommand: CommandDefinition
    private lateinit var runOptions: ExecutionOptions
    private lateinit var service: CommandService
    private lateinit var sessionHandle: ScriptedInteractiveSession

    @BeforeTest
    fun setUp() {
        engine = RecordingExecutionEngine()
        baseCommand =
            CommandDefinition
                .builder()
                .command(listOf("python"))
                .putEnvironment("BASE", "1")
                .build()
        runOptions =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.bounded(1024))
                .stderrPolicy(OutputCapture.discard())
                .build()
        service = CommandService(engine, baseCommand, runOptions)
        sessionHandle = ScriptedInteractiveSession()
        engine.sessionHandleFactory = { sessionHandle }
    }

    @Test
    fun `run delegates with base command`() {
        engine.runResponse = ProcessResult(0, "ok", "", Optional.of(Duration.ZERO))

        val result = service.runner().run()

        val spec = requireNotNull(engine.lastRunSpec)
        val options = requireNotNull(engine.lastRunOptions)

        assertEquals(listOf("python"), spec.command())
        assertEquals(runOptions, options)
        assertTrue(result.success)
        assertEquals("ok", result.value)
    }

    @Test
    fun `run with extra args and custom options`() {
        engine.runResponse = ProcessResult(2, "oops", "err", Optional.empty())

        val outcome =
            service.runner().run { builder ->
                builder
                    .subcommand("-c")
                    .args("print('hi')")
                    .customizeOptions { options -> options.mergeErrorIntoOutput(true) }
            }

        val spec = requireNotNull(engine.lastRunSpec)
        val options = requireNotNull(engine.lastRunOptions)

        assertEquals(listOf("python", "-c", "print('hi')"), spec.command())
        assertTrue(options.mergeErrorIntoOutput())

        assertTrue(outcome.success.not())
        val error = outcome.error as ProcessExecutionException
        assertEquals(2, error.exitCode())
        assertEquals("oops", error.stdout())
        assertEquals("err", error.stderr())
    }

    @Test
    fun `open line session uses session defaults`() {
        val session = service.lineSessionRunner().open { builder -> builder.args("-i") }

        val spec = requireNotNull(engine.lastSessionSpec)
        val options = requireNotNull(engine.lastSessionOptions)

        assertEquals(listOf("python", "-i"), spec.command())
        assertEquals(runOptions, options)

        sessionHandle.queueResponse("42")
        val result = session.process("print(42)")
        assertTrue(result.success)
        assertEquals("42", result.value)
        assertEquals("print(42)\n", sessionHandle.consumedInput())
    }

    @Test
    fun `command call builder augments environment and working directory`() {
        engine.runResponse = ProcessResult(0, "done", "", Optional.empty())

        val call =
            CommandCallBuilder
                .from(baseCommand, runOptions, LineDelimitedResponseDecoder())
                .env("VAR", "42")
                .workingDirectory(
                    java.nio.file.Path
                        .of("/tmp"),
                ).option("--flag")
                .build()

        val result = service.runner().run(call)

        val spec = requireNotNull(engine.lastRunSpec)
        val env = spec.environment()

        assertEquals("42", env["VAR"])
        assertEquals("1", env["BASE"])
        assertEquals(
            java.nio.file.Path
                .of("/tmp"),
            spec.workingDirectory(),
        )
        assertTrue(result.success)
    }

    @Test
    fun `line session supports custom decoder`() {
        val decoder =
            ResponseDecoder { stdout, charset ->
                val base = LineDelimitedResponseDecoder().read(stdout, charset)
                "$base-custom"
            }

        sessionHandle.queueResponse("result")
        val session = service.lineSessionRunner().open { builder -> builder.args("-i").decoder(decoder) }

        val outcome = session.process("noop")
        assertTrue(outcome.success)
        assertEquals("result-custom", outcome.value)
    }

    @Test
    fun `line session runner reuses defaults`() {
        sessionHandle.queueResponse("pong")

        val session = service.lineSessionRunner().open { builder -> builder.args("-i") }

        val spec = requireNotNull(engine.lastSessionSpec)
        val options = requireNotNull(engine.lastSessionOptions)

        assertEquals(listOf("python", "-i"), spec.command())
        assertEquals(runOptions, options)

        val outcome = session.process("ping")
        assertTrue(outcome.success)
        assertEquals("pong", outcome.value)
    }

    @Test
    fun `line session surfaces IO failures as LineSessionException`() {
        val session = service.lineSessionRunner().open()

        val outcome = session.process("ping")

        assertFalse(outcome.success)
        val error = assertIs<LineSessionException>(outcome.error)
        assertEquals("ping", error.input())
        assertIs<java.io.IOException>(error.cause)
    }

    @Test
    fun `interactive session exposes raw handle`() {
        val interactive = service.interactiveSessionRunner().open()

        assertSame(sessionHandle.stdin(), interactive.stdin())
        assertSame(sessionHandle.stdout(), interactive.stdout())
        assertSame(sessionHandle.stderr(), interactive.stderr())
        assertSame(sessionHandle.onExit(), interactive.onExit())
        assertSame(sessionHandle, interactive.handle())
    }

    @Test
    fun `line session closeStdin delegates to handle`() {
        val session = service.lineSessionRunner().open()

        session.closeStdin()

        assertTrue(sessionHandle.stdinClosed)
    }

    @Test
    fun `interactive session forwards signal and resize helpers`() {
        val interactive = service.interactiveSessionRunner().open()

        interactive.sendSignal(ShutdownSignal.INTERRUPT)
        interactive.resizePty(120, 40)

        assertEquals(ShutdownSignal.INTERRUPT, sessionHandle.lastSignal)
        assertEquals(120 to 40, sessionHandle.lastResize)
    }

    @Test
    fun `interactive session defaults to PTY when unspecified`() {
        service.interactiveSessionRunner().open()

        val spec = engine.sessionInvocations.last()
        assertEquals(TerminalPreference.REQUIRED, spec.terminalPreference())
        assertEquals(1, engine.sessionInvocations.size)
    }

    @Test
    fun `interactive session runner opens session`() {
        val call =
            CommandCallBuilder
                .from(baseCommand, runOptions, LineDelimitedResponseDecoder())
                .args("-i")
                .build()

        val interactive = service.interactiveSessionRunner().open(call)

        val spec = requireNotNull(engine.lastSessionSpec)
        assertEquals(listOf("python", "-i"), spec.command())
        assertSame(sessionHandle.stdin(), interactive.stdin())
    }

    @Test
    fun `session builder honours explicit terminal preference`() {
        service.lineSessionRunner().open { builder ->
            builder.terminalPreference(TerminalPreference.DISABLED)
        }

        val spec = engine.sessionInvocations.last()
        assertEquals(TerminalPreference.DISABLED, spec.terminalPreference())
    }

    @Test
    fun `interactive session falls back to pipes when PTY unavailable`() {
        engine.sessionStartFailures.add(UnsupportedOperationException("no pty"))

        val interactive = service.interactiveSessionRunner().open()

        assertSame(sessionHandle, interactive.handle())
        assertEquals(2, engine.sessionInvocations.size)
        assertEquals(TerminalPreference.REQUIRED, engine.sessionInvocations[0].terminalPreference())
        assertEquals(TerminalPreference.DISABLED, engine.sessionInvocations[1].terminalPreference())
    }

    @Test
    fun `runAsync delegates to scheduler`() {
        val scheduler = ImmediateClientScheduler()
        service = CommandService(engine, baseCommand, runOptions, scheduler)
        engine.runResponse = ProcessResult(0, "value", "", Optional.empty())

        val future = service.runner().runAsync()

        assertEquals(1, scheduler.submitted.size)
        val result = future.get()
        assertTrue(result.success)
        assertEquals("value", result.value)
    }

    @Test
    fun `line session processAsync returns future result`() {
        val scheduler = ImmediateClientScheduler()
        service = CommandService(engine, baseCommand, runOptions, scheduler)
        sessionHandle.queueResponse("async-ok")

        val session = service.lineSessionRunner().open()
        val future = session.processAsync("println('hi')")

        assertEquals(1, scheduler.submitted.size)
        val outcome = future.get()
        assertTrue(outcome.success)
        assertEquals("async-ok", outcome.value)
    }
}
