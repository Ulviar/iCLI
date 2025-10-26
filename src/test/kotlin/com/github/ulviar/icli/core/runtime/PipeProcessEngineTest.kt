package com.github.ulviar.icli.core.runtime

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.OutputCapture
import com.github.ulviar.icli.core.ProcessEngine
import com.github.ulviar.icli.core.ShutdownPlan
import com.github.ulviar.icli.core.ShutdownSignal
import com.github.ulviar.icli.core.TerminalPreference
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PipeProcessEngineTest {
    private lateinit var engine: ProcessEngine

    @BeforeTest
    fun setUp() {
        engine = PipeProcessEngine()
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
    fun `run rejects streaming capture until implemented`() {
        val spec = spec("--stdout", "noop")
        val options = ExecutionOptions.builder().stdoutPolicy(OutputCapture.streaming()).build()

        assertFailsWith<UnsupportedOperationException> {
            engine.run(spec, options)
        }
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

    @Test
    fun `run rejects required PTY preference`() {
        val spec =
            CommandDefinition
                .builder()
                .command(testProcessCommand("--stdout", "noop"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        assertFailsWith<UnsupportedOperationException> {
            engine.run(spec, ExecutionOptions.builder().build())
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
