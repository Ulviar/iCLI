package com.github.ulviar.icli.engine.runtime

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.ShellConfiguration
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardProcessEngineIntegrationTest {
    private lateinit var engine: StandardProcessEngine
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @BeforeTest
    fun setUp() {
        engine = StandardProcessEngine()
    }

    @AfterTest
    fun tearDown() {
        // no-op
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `runs unix shell command`() {
        val spec = shellCommandBuilder("printf integration").build()

        val result = engine.run(spec, ExecutionOptions.builder().build())

        assertEquals("integration", result.stdout())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `runs windows shell command`() {
        val spec = shellCommandBuilder("echo integration").build()

        val result = engine.run(spec, ExecutionOptions.builder().build())

        assertEquals("integration", result.stdout().trim())
    }

    @Test
    fun `passes environment variables to shell`() {
        val envVar = "ICLI_INTEGRATION_VAR"
        val spec =
            shellCommandBuilder(shellEchoVariableCommand(envVar))
                .putEnvironment(envVar, "env-ok")
                .build()

        val result = engine.run(spec, ExecutionOptions.builder().build())

        assertEquals("env-ok", result.stdout().trim())
    }

    @Test
    fun `uses specified working directory`() {
        val tempDir = Files.createTempDirectory("icli-integration")
        try {
            val spec =
                shellCommandBuilder(shellPrintWorkingDirectoryCommand())
                    .workingDirectory(tempDir)
                    .build()

            val result = engine.run(spec, ExecutionOptions.builder().build())

            val expected = tempDir.toRealPath().toString()
            val actual = result.stdout().trim()
            assertTrue(actual.equals(expected, ignoreCase = isWindows))
        } finally {
            Files.deleteIfExists(tempDir)
        }
    }

    private fun shellEchoVariableCommand(name: String): String = if (isWindows) "echo %$name%" else "echo ${'$'}$name"

    private fun shellPrintWorkingDirectoryCommand(): String = if (isWindows) "cd" else "pwd"

    private fun shellCommandBuilder(commandLine: String): CommandDefinition.Builder {
        val shell =
            if (isWindows) {
                ShellConfiguration.builder().command("cmd.exe", "/c").build()
            } else {
                ShellConfiguration.builder().command("/bin/sh", "-c").build()
            }
        return CommandDefinition.builder().shell(shell).command(commandLine)
    }
}
