package com.github.ulviar.icli.engine.runtime

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.OutputCapture
import com.github.ulviar.icli.engine.ShutdownPlan
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.diagnostics.DiagnosticsEvent
import com.github.ulviar.icli.engine.diagnostics.DiagnosticsListener
import com.github.ulviar.icli.engine.diagnostics.StreamType
import com.github.ulviar.icli.engine.runtime.ProcessEngineExecutionException
import com.github.ulviar.icli.engine.runtime.internal.io.OutputSink
import com.github.ulviar.icli.engine.runtime.internal.io.OutputSinkFactory
import com.github.ulviar.icli.engine.runtime.internal.io.StreamDrainer
import com.github.ulviar.icli.engine.runtime.internal.launch.PipeCommandLauncher
import com.github.ulviar.icli.engine.runtime.internal.launch.ProcessBuilderStarter
import com.github.ulviar.icli.engine.runtime.internal.launch.PtyCommandLauncher
import com.github.ulviar.icli.engine.runtime.internal.launch.TerminalAwareCommandLauncher
import com.github.ulviar.icli.engine.runtime.internal.shutdown.ShutdownExecutor
import com.github.ulviar.icli.engine.runtime.internal.shutdown.TreeAwareProcessTerminator
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StandardProcessEngineTest {
    private lateinit var engine: StandardProcessEngine

    @BeforeTest
    fun setUp() {
        engine = StandardProcessEngine()
    }

    @AfterTest
    fun tearDown() {
        // nothing yet
    }

    @Test
    fun `run returns stdout per spec`() {
        val spec = spec("--stdout", "hello")

        val result = engine.run(spec, ExecutionOptions.builder().build())

        assertEquals(0, result.exitCode())
        assertEquals("hello\n", result.stdout())
        assertEquals("", result.stderr())
        assertTrue(result.duration().isPresent)
    }

    @Test
    fun `run keeps stderr separate by default`() {
        val spec = spec("--stderr", "oops", "--exit", "3")

        val result = engine.run(spec, ExecutionOptions.builder().build())

        assertEquals(3, result.exitCode())
        assertEquals("", result.stdout())
        assertEquals("oops\n", result.stderr())
    }

    @Test
    fun `run merges stderr into stdout when configured`() {
        val spec = spec("--stdout", "one", "--stderr", "two")
        val options = ExecutionOptions.builder().mergeErrorIntoOutput(true).build()

        val result = engine.run(spec, options)

        val lines = result.stdout().lines().filter { it.isNotBlank() }
        assertEquals(listOf("one", "two"), lines)
        assertEquals("", result.stderr())
    }

    @Test
    fun `run enforces stdout capture limit`() {
        val spec = spec("--repeat", "abc", "64")
        val options = ExecutionOptions.builder().stdoutPolicy(OutputCapture.bounded(8)).build()

        val result = engine.run(spec, options)

        assertEquals(8, result.stdout().length)
    }

    @Test
    fun `run enforces stderr capture limit`() {
        val spec = spec("--stderr", "abcdef")
        val options = ExecutionOptions.builder().stderrPolicy(OutputCapture.bounded(3)).build()

        val result = engine.run(spec, options)

        assertEquals("abc", result.stderr())
    }

    @Test
    fun `run supports discard policy`() {
        val spec = spec("--stdout", "noise")
        val options = ExecutionOptions.builder().stdoutPolicy(OutputCapture.discard()).build()

        val result = engine.run(spec, options)

        assertEquals("", result.stdout())
    }

    @Test
    fun `run streams output through diagnostics listener`() {
        val spec = spec("--stdout", "streaming")
        val recorder = RecordingDiagnostics()
        val options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.streaming())
                .diagnosticsListener(recorder)
                .build()

        val result = engine.run(spec, options)

        assertEquals("", result.stdout())
        val chunk = recorder.events.filterIsInstance<DiagnosticsEvent.OutputChunk>().single()
        assertEquals(StreamType.STDOUT, chunk.stream())
        assertEquals("streaming\n", chunk.text(chunk.charset()))
    }

    @Test
    fun `run streams stderr through diagnostics listener`() {
        val spec = spec("--stderr", "alert")
        val recorder = RecordingDiagnostics()
        val options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.discard())
                .stderrPolicy(OutputCapture.streaming())
                .diagnosticsListener(recorder)
                .build()

        val result = engine.run(spec, options)

        assertEquals("", result.stderr())
        val chunk = recorder.events.filterIsInstance<DiagnosticsEvent.OutputChunk>().single()
        assertEquals(StreamType.STDERR, chunk.stream())
        assertEquals("alert\n", chunk.text(chunk.charset()))
    }

    @Test
    fun `run merges stderr triggers merged stream diagnostics`() {
        val spec = spec("--stdout", "one", "--stderr", "two")
        val recorder = RecordingDiagnostics()
        val options =
            ExecutionOptions
                .builder()
                .mergeErrorIntoOutput(true)
                .stdoutPolicy(OutputCapture.streaming())
                .diagnosticsListener(recorder)
                .build()

        val result = engine.run(spec, options)

        assertEquals("", result.stdout())
        val chunkStreams =
            recorder.events
                .filterIsInstance<DiagnosticsEvent.OutputChunk>()
                .map { it.stream() }
                .toSet()
        assertEquals(setOf(StreamType.MERGED), chunkStreams)
    }

    @Test
    fun `run propagates diagnostics listener failure`() {
        val spec = spec("--stdout", "data")
        val options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.streaming())
                .diagnosticsListener { throw IllegalStateException("boom") }
                .build()

        val error =
            assertFailsWith<ProcessEngineExecutionException> {
                engine.run(spec, options)
            }
        val completion = error.cause
        assertTrue(completion is java.util.concurrent.CompletionException)
        assertTrue(completion.cause is IllegalStateException)
    }

    @Test
    fun `run emits truncation diagnostics from bounded capture`() {
        val spec = spec("--stdout", "overflow")
        val recorder = RecordingDiagnostics()
        val options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(OutputCapture.bounded(4))
                .diagnosticsListener(recorder)
                .build()

        val result = engine.run(spec, options)

        assertEquals("over", result.stdout())
        val truncated = recorder.events.filterIsInstance<DiagnosticsEvent.OutputTruncated>().single()
        assertEquals(StreamType.STDOUT, truncated.stream())
        assertEquals(4, truncated.retainedBytes())
        assertEquals(5, truncated.discardedBytes())
        assertEquals("flow\n", truncated.preview(StandardCharsets.UTF_8))
    }

    @Test
    fun `run wraps stream drainer failures`() {
        val failingDrainer =
            object : StreamDrainer {
                private val first = AtomicBoolean(true)

                override fun drain(
                    input: InputStream,
                    sink: OutputSink,
                ): CompletableFuture<Void> =
                    if (first.getAndSet(false)) {
                        CompletableFuture.failedFuture(RuntimeException("boom"))
                    } else {
                        CompletableFuture.completedFuture(null)
                    }
            }

        val failingEngine =
            StandardProcessEngine(
                TerminalAwareCommandLauncher(PipeCommandLauncher(ProcessBuilderStarter()), PtyCommandLauncher()),
                OutputSinkFactory(),
                failingDrainer,
                ShutdownExecutor(TreeAwareProcessTerminator()),
                Clock.systemUTC(),
            )

        val exception =
            assertFailsWith<ProcessEngineExecutionException> {
                failingEngine.run(spec("--stdout", "data"), ExecutionOptions.builder().build())
            }
        assertTrue(exception.message?.contains("drain process output") == true)
    }

    @Test
    fun `run propagates env and working directory`() {
        val tempDir = Files.createTempDirectory("icli-env")
        try {
            val spec =
                CommandDefinition
                    .builder()
                    .command(testProcessCommand("--print-env", "FOO", "--print-cwd"))
                    .putEnvironment("FOO", "bar")
                    .workingDirectory(tempDir)
                    .build()

            val result = engine.run(spec, ExecutionOptions.builder().build())
            val lines = result.stdout().lines().filter { it.isNotBlank() }

            assertEquals("bar", lines.first())
            assertEquals(tempDir.toRealPath().toString(), lines.last())
        } finally {
            Files.deleteIfExists(tempDir)
        }
    }

    private class RecordingDiagnostics : DiagnosticsListener {
        val events: MutableList<DiagnosticsEvent> = mutableListOf()

        override fun onEvent(event: DiagnosticsEvent) {
            events += event
        }
    }

    @Test
    fun `run honours required PTY preference when available`() {
        val spec =
            CommandDefinition
                .builder()
                .command(testProcessCommand("--stdout", "noop"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        val options = ExecutionOptions.builder().build()

        try {
            engine.run(spec, options)
        } catch (ex: UnsupportedOperationException) {
            // PTY support may be unavailable on some CI agents; ensure the engine surfaces the failure clearly.
            assertTrue(ex.message?.contains("PTY") == true)
        }
    }

    @Test
    fun `run obeys shutdown plan timeouts`() {
        val spec = spec("--sleep-ms", "2000")
        val options =
            ExecutionOptions
                .builder()
                .shutdownPlan(ShutdownPlan(Duration.ofMillis(200), Duration.ofMillis(100), ShutdownSignal.INTERRUPT))
                .build()

        val result = engine.run(spec, options)

        assertTrue(result.exitCode() != 0)
        assertTrue(result.duration().orElse(Duration.ZERO) < Duration.ofSeconds(2))
    }

    @Test
    fun `startSession echoes stdin over pipes`() {
        val spec = spec("--echo-stdin")

        val session = engine.startSession(spec, ExecutionOptions.builder().build())

        session.stdin().write("hello\n".toByteArray(StandardCharsets.UTF_8))
        session.stdin().flush()

        val reader = BufferedReader(InputStreamReader(session.stdout(), StandardCharsets.UTF_8))
        val echoed = reader.readLine()
        assertEquals("hello", echoed)

        session.closeStdin()
        val exitCode = session.onExit().get(2, TimeUnit.SECONDS)
        assertEquals(0, exitCode)
        session.close()
    }

    @Test
    fun `close enforces shutdown plan for stuck sessions`() {
        val spec = spec("--sleep-ms", "5000")
        val options =
            ExecutionOptions
                .builder()
                .shutdownPlan(ShutdownPlan(Duration.ofMillis(200), Duration.ofMillis(100), ShutdownSignal.INTERRUPT))
                .idleTimeout(Duration.ofMinutes(5))
                .build()

        val session = engine.startSession(spec, options)

        session.close()

        val exitCode = session.onExit().get(5, TimeUnit.SECONDS)
        assertTrue(exitCode != 0)
    }

    @Test
    fun `idle timeout closes inactive session`() {
        val spec = spec("--sleep-ms", "5000")
        val options =
            ExecutionOptions
                .builder()
                .idleTimeout(Duration.ofMillis(150))
                .shutdownPlan(ShutdownPlan(Duration.ofMillis(50), Duration.ofMillis(50), ShutdownSignal.INTERRUPT))
                .build()

        val session = engine.startSession(spec, options)

        val exitCode = session.onExit().get(5, TimeUnit.SECONDS)
        assertTrue(exitCode != 0)
        session.close()
    }

    @Test
    fun `stdin activity resets idle timeout`() {
        val spec = spec("--echo-stdin")
        val options =
            ExecutionOptions
                .builder()
                .idleTimeout(Duration.ofMillis(150))
                .shutdownPlan(ShutdownPlan(Duration.ofMillis(50), Duration.ofMillis(50), ShutdownSignal.INTERRUPT))
                .build()

        val session = engine.startSession(spec, options)
        val writer = OutputStreamWriter(session.stdin(), StandardCharsets.UTF_8)
        val reader = BufferedReader(InputStreamReader(session.stdout(), StandardCharsets.UTF_8))

        writer.write("ping\n")
        writer.flush()
        assertEquals("ping", reader.readLine())
        Thread.sleep(100)

        writer.write("pong\n")
        writer.flush()
        assertEquals("pong", reader.readLine())

        Thread.sleep(200)

        val exitCode = session.onExit().get(5, TimeUnit.SECONDS)
        assertTrue(exitCode != 0)
        session.close()
    }

    @Test
    fun `observer notified on idle timeout`() {
        val observed = AtomicReference<Duration>()
        val spec = spec("--sleep-ms", "5000")
        val options =
            ExecutionOptions
                .builder()
                .idleTimeout(Duration.ofMillis(120))
                .shutdownPlan(ShutdownPlan(Duration.ofMillis(50), Duration.ofMillis(50), ShutdownSignal.INTERRUPT))
                .sessionObserver { command, timeout ->
                    assertEquals(spec.command(), command.command())
                    observed.set(timeout)
                }.build()

        val session = engine.startSession(spec, options)
        session.onExit().get(5, TimeUnit.SECONDS)
        assertEquals(Duration.ofMillis(120), observed.get())
        session.close()
    }

    private fun spec(vararg args: String): CommandDefinition =
        CommandDefinition
            .builder()
            .command(testProcessCommand(*args))
            .build()

    private fun testProcessCommand(vararg args: String): List<String> {
        val javaHome = System.getProperty("java.home")
        val javaExec =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                Path.of(javaHome, "bin", "java.exe")
            } else {
                Path.of(javaHome, "bin", "java")
            }

        val classpath = System.getProperty("java.class.path")
        val mainClass = "com.github.ulviar.icli.testing.processes.TestProcess"

        return buildList {
            add(javaExec.toString())
            add("-cp")
            add(classpath)
            add(mainClass)
            addAll(args.toList())
        }
    }
}
