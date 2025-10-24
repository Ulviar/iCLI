package com.github.ulviar.icli.client

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.OutputCapture
import com.github.ulviar.icli.core.ProcessResult
import com.github.ulviar.icli.core.ShutdownPlan
import com.github.ulviar.icli.core.ShutdownSignal
import com.github.ulviar.icli.testing.RecordingExecutionEngine
import com.github.ulviar.icli.testing.ScriptedInteractiveSession
import java.time.Duration
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandServiceTest {
    private lateinit var engine: RecordingExecutionEngine
    private lateinit var baseCommand: CommandDefinition
    private lateinit var runOptions: ExecutionOptions
    private lateinit var sessionOptions: ExecutionOptions
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
        sessionOptions =
            ExecutionOptions
                .builder()
                .shutdownPlan(ShutdownPlan(Duration.ofSeconds(5), Duration.ofSeconds(1), ShutdownSignal.TERMINATE))
                .build()
        service = CommandService(engine, baseCommand, runOptions)
        sessionHandle = ScriptedInteractiveSession()
        engine.sessionHandleFactory = { sessionHandle }
    }

    @Test
    fun `run delegates with base command`() {
        engine.runResponse = ProcessResult(0, "ok", "", Optional.of(Duration.ZERO))

        val result = service.run()

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
            service.run { builder ->
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
        val session =
            service.openLineSession { builder ->
                builder.args("-i")
            }

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

        val result = service.run(call)

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
        val session =
            service.openLineSession { builder ->
                builder.args("-i").decoder(decoder)
            }

        val outcome = session.process("noop")
        assertTrue(outcome.success)
        assertEquals("result-custom", outcome.value)
    }

    @Test
    fun `interactive session exposes raw handle`() {
        val interactive = service.openInteractiveSession()

        assertSame(sessionHandle.stdin(), interactive.stdin())
        assertSame(sessionHandle.stdout(), interactive.stdout())
        assertSame(sessionHandle.stderr(), interactive.stderr())
        assertSame(sessionHandle.onExit(), interactive.onExit())
        assertSame(sessionHandle, interactive.handle())
    }
}
