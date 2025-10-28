package com.github.ulviar.icli.client

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.TerminalPreference
import com.github.ulviar.icli.core.runtime.StandardProcessEngine
import com.github.ulviar.icli.testing.TestProcessCommand
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineSessionClientIntegrationTest {
    @Test
    fun `pipe-backed line session processes sequential commands`() {
        val service = commandService()

        val session =
            service.lineSessionRunner().open { builder ->
                builder.terminalPreference(TerminalPreference.DISABLED)
            }

        session.use {
            assertEquals("READY", consumeLine(it))

            assertLineResult("OUT:ping", it.process("ping"))

            val asyncResult = it.processAsync("pong").get(5, TimeUnit.SECONDS)
            assertLineResult("OUT:pong", asyncResult)

            assertLineResult("OUT:exit", it.process("exit"))
            it.onExit().get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `pty-backed line session processes sequential commands`() {
        val service = commandService()

        val session =
            try {
                service.lineSessionRunner().open { builder ->
                    builder.terminalPreference(TerminalPreference.REQUIRED)
                }
            } catch (ex: UnsupportedOperationException) {
                Assumptions.assumeTrue(false, "PTY unavailable: ${ex.message}")
                return
            }

        session.use {
            assertEquals("READY", consumeLine(it))

            assertLineResult("ping", it.process("ping"))
            assertEquals("OUT:ping", consumeLine(it))

            assertLineResult("pong", it.process("pong"))
            assertEquals("OUT:pong", consumeLine(it))

            assertLineResult("exit", it.process("exit"))
            assertEquals("OUT:exit", consumeLine(it))

            it.onExit().get(5, TimeUnit.SECONDS)
        }
    }

    private fun commandService(): CommandService {
        val engine = StandardProcessEngine()
        val command =
            CommandDefinition
                .builder()
                .command(TestProcessCommand.command("--interactive"))
                .build()
        return CommandService(engine, command, ExecutionOptions.builder().build())
    }

    private fun consumeLine(session: LineSessionClient): String =
        LineDelimitedResponseDecoder()
            .read(session.interactive().stdout(), StandardCharsets.UTF_8)
            .trimEnd('\r')

    private fun assertLineResult(
        expected: String,
        result: CommandResult<String>,
    ) {
        assertTrue(result.success)
        val value = requireNotNull(result.value)
        assertEquals(expected, value.trimEnd('\r'))
    }
}
