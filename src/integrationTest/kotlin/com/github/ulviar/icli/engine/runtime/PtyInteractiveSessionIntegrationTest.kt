package com.github.ulviar.icli.engine.runtime

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class PtyInteractiveSessionIntegrationTest {
    private val engine = StandardProcessEngine()

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `cat echoes input when PTY is available`() {
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("/bin/cat"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        val session =
            try {
                engine.startSession(spec, ExecutionOptions.builder().build())
            } catch (ex: UnsupportedOperationException) {
                Assumptions.assumeTrue(false, "PTY unavailable: ${ex.message}")
                return
            }

        try {
            val writer = OutputStreamWriter(session.stdin(), StandardCharsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(session.stdout(), StandardCharsets.UTF_8))

            writer.write("hello-pty\n")
            writer.flush()

            val echoed = reader.readLine()
            assertEquals("hello-pty", echoed.trimEnd('\r'))

            session.closeStdin()
            session.onExit().get(5, TimeUnit.SECONDS)
        } finally {
            session.close()
        }
    }
}
