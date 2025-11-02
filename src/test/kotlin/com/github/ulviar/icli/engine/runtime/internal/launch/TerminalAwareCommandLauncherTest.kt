package com.github.ulviar.icli.engine.runtime.internal.launch

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.runtime.internal.terminal.TerminalController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalAwareCommandLauncherTest {
    @Test
    fun `uses PTY launcher when terminal required`() {
        val pipe = RecordingLauncher()
        val pty = RecordingLauncher()
        val launcher = TerminalAwareCommandLauncher(pipe, pty)
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        launcher.launch(spec, false)

        assertEquals(0, pipe.invocations)
        assertEquals(1, pty.invocations)
    }

    @Test
    fun `uses pipe when terminal disabled`() {
        val pipe = RecordingLauncher()
        val pty = RecordingLauncher()
        val launcher = TerminalAwareCommandLauncher(pipe, pty)
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .terminalPreference(TerminalPreference.DISABLED)
                .build()

        launcher.launch(spec, false)

        assertEquals(1, pipe.invocations)
        assertEquals(0, pty.invocations)
    }

    @Test
    fun `uses pipes by default when preference is auto`() {
        val pipe = RecordingLauncher()
        val pty = RecordingLauncher()
        val launcher = TerminalAwareCommandLauncher(pipe, pty)
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .terminalPreference(TerminalPreference.AUTO)
                .build()

        launcher.launch(spec, false)

        assertEquals(1, pipe.invocations)
        assertEquals(0, pty.invocations)
    }

    @Test
    fun `rethrows when PTY required but unavailable`() {
        val pipe = RecordingLauncher()
        val pty = RecordingLauncher(UnsupportedOperationException("no pty"))
        val launcher = TerminalAwareCommandLauncher(pipe, pty)
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        assertFailsWith<UnsupportedOperationException> {
            launcher.launch(spec, false)
        }

        assertEquals(0, pipe.invocations)
        assertEquals(1, pty.invocations)
    }

    private class RecordingLauncher(
        private val failure: RuntimeException? = null,
    ) : CommandLauncher {
        var invocations: Int = 0
            private set

        override fun launch(
            spec: CommandDefinition,
            redirectErrorStream: Boolean,
        ): CommandLauncher.LaunchedProcess {
            invocations += 1
            failure?.let { throw it }
            return CommandLauncher.LaunchedProcess(FakeProcess(), spec.command(), TerminalController.NO_OP)
        }
    }

    private class FakeProcess : Process() {
        override fun destroy() {}

        override fun exitValue(): Int = 0

        override fun getOutputStream(): java.io.OutputStream = throw UnsupportedOperationException()

        override fun getInputStream(): java.io.InputStream = throw UnsupportedOperationException()

        override fun getErrorStream(): java.io.InputStream = throw UnsupportedOperationException()

        override fun waitFor(): Int = 0
    }
}
