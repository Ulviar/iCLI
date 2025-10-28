package com.github.ulviar.icli.client

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.InteractiveSession
import com.github.ulviar.icli.core.OutputCapture
import com.github.ulviar.icli.core.ProcessEngine
import com.github.ulviar.icli.core.ProcessResult
import com.github.ulviar.icli.core.TerminalPreference
import com.github.ulviar.icli.testing.ImmediateClientScheduler
import com.github.ulviar.icli.testing.RecordingExecutionEngine
import java.time.Duration
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandRunnerTest {
    private lateinit var engine: RecordingExecutionEngine
    private lateinit var baseCommand: CommandDefinition
    private lateinit var options: ExecutionOptions
    private lateinit var scheduler: ImmediateClientScheduler
    private lateinit var runner: CommandRunner

    @BeforeTest
    fun setUp() {
        engine = RecordingExecutionEngine()
        baseCommand =
            CommandDefinition
                .builder()
                .command(listOf("python"))
                .environment(mapOf("BASE" to "1"))
                .build()
        options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.bounded(1024))
                .stderrPolicy(OutputCapture.discard())
                .build()
        scheduler = ImmediateClientScheduler()
        runner = CommandRunner(engine, baseCommand, options, scheduler, LineDelimitedResponseDecoder())
    }

    @Test
    fun `run returns success result for zero exit code`() {
        engine.runResponse = ProcessResult(0, "ok", "", Optional.of(Duration.ZERO))

        val result = runner.run()

        assertTrue(result.success)
        assertEquals("ok", result.value)
        assertSame(baseCommand, engine.lastRunSpec)
        assertSame(options, engine.lastRunOptions)
    }

    @Test
    fun `run returns failure with diagnostics for non zero exit`() {
        engine.runResponse = ProcessResult(5, "partial", "error", Optional.empty())

        val outcome = runner.run()

        assertFalse(outcome.success)
        val exception = outcome.error as ProcessExecutionException
        assertEquals(5, exception.exitCode())
        assertEquals("partial", exception.stdout())
        assertEquals("error", exception.stderr())
    }

    @Test
    fun `run applies command builder customisations`() {
        engine.runResponse = ProcessResult(0, "value", "", Optional.empty())

        val result =
            runner.run { builder ->
                builder
                    .subcommand("-c")
                    .args("print('hi')")
                    .env("EXTRA", "42")
                    .workingDirectory(
                        java.nio.file.Path
                            .of("/tmp"),
                    ).terminalPreference(TerminalPreference.DISABLED)
                    .customizeOptions { derived -> derived.mergeErrorIntoOutput(true) }
            }

        val spec = requireNotNull(engine.lastRunSpec)
        val recordedOptions = requireNotNull(engine.lastRunOptions)

        assertEquals(listOf("python", "-c", "print('hi')"), spec.command())
        assertEquals("42", spec.environment()["EXTRA"])
        assertEquals(
            java.nio.file.Path
                .of("/tmp"),
            spec.workingDirectory(),
        )
        assertEquals(TerminalPreference.DISABLED, spec.terminalPreference())
        assertTrue(recordedOptions.mergeErrorIntoOutput())
        assertTrue(result.success)
    }

    @Test
    fun `run supports explicit command call`() {
        engine.runResponse = ProcessResult(0, "call", "", Optional.empty())
        val call =
            CommandCallBuilder
                .from(baseCommand, options, LineDelimitedResponseDecoder())
                .args("-m", "module")
                .build()

        val result = runner.run(call)

        assertTrue(result.success)
        val spec = requireNotNull(engine.lastRunSpec)
        assertEquals(listOf("python", "-m", "module"), spec.command())
    }

    @Test
    fun `runAsync delegates to scheduler`() {
        engine.runResponse = ProcessResult(0, "async", "", Optional.empty())

        val future = runner.runAsync()

        assertEquals(1, scheduler.submitted.size)
        val result = future.get()
        assertTrue(result.success)
        assertEquals("async", result.value)
    }

    @Test
    fun `run returns failure when engine throws runtime exception`() {
        val failure = IllegalStateException("boom")
        val throwingRunner =
            CommandRunner(
                object : ProcessEngine {
                    override fun run(
                        spec: CommandDefinition,
                        options: ExecutionOptions,
                    ): ProcessResult = throw failure

                    override fun startSession(
                        spec: CommandDefinition,
                        options: ExecutionOptions,
                    ): InteractiveSession = throw UnsupportedOperationException("not used")
                },
                baseCommand,
                options,
                scheduler,
                LineDelimitedResponseDecoder(),
            )

        val outcome = throwingRunner.run()

        assertFalse(outcome.success)
        val error = outcome.error as CommandRunnerException
        assertSame(failure, error.cause)
        assertEquals(listOf("python"), error.call().command().command())
        assertEquals("python", error.call().renderCommandLine())
    }
}
