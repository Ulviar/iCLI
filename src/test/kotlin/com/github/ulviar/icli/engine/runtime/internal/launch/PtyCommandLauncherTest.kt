package com.github.ulviar.icli.engine.runtime.internal.launch

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ShellConfiguration
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.runtime.internal.terminal.TerminalController
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PtyCommandLauncherTest {
    @Test
    fun `launch delegates to factory and controller`() {
        val factory = RecordingPtyProcessFactory()
        val controller = TerminalController.NO_OP
        val launcher = PtyCommandLauncher(factory) { controller }
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .shell(ShellConfiguration.builder().command("/bin/sh", "-c").build())
                .terminalPreference(TerminalPreference.REQUIRED)
                .putEnvironment("FOO", "bar")
                .workingDirectory(Path.of("/tmp"))
                .build()

        val launched = launcher.launch(spec, true)

        val request = requireNotNull(factory.lastRequest)
        assertEquals(listOf("/bin/sh", "-c", "echo", "hi"), request.commandLine())
        assertEquals(Path.of("/tmp"), request.workingDirectory())
        assertEquals(true, request.redirectErrorStream())
        assertEquals("bar", request.environment()["FOO"])
        assertEquals("xterm-256color", request.environment()["TERM"])
        val expectedConPty = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        assertEquals(expectedConPty, request.useWinConPty())
        assertSame(controller, launched.terminalController())
    }

    @Test
    fun `rejects disabled preference`() {
        val launcher = PtyCommandLauncher(RecordingPtyProcessFactory()) { TerminalController.NO_OP }
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo"))
                .terminalPreference(TerminalPreference.DISABLED)
                .build()

        assertFailsWith<UnsupportedOperationException> {
            launcher.launch(spec, false)
        }
    }

    @Test
    fun `wraps io failures`() {
        val launcher = PtyCommandLauncher(FailingPtyProcessFactory(IOException("boom"))) { TerminalController.NO_OP }
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        assertFailsWith<UncheckedIOException> {
            launcher.launch(spec, false)
        }
    }

    @Test
    fun `propagates unsupported operation`() {
        val launcher = PtyCommandLauncher(FailingPtyProcessFactory(UnsupportedOperationException("no pty"))) { TerminalController.NO_OP }
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        assertFailsWith<UnsupportedOperationException> {
            launcher.launch(spec, false)
        }
    }

    private class RecordingPtyProcessFactory : PtyProcessFactory {
        var lastRequest: PtyLaunchRequest? = null
        private val process = FakePtyProcess()

        override fun start(request: PtyLaunchRequest): PtyProcess {
            lastRequest = request
            return process
        }
    }

    private class FailingPtyProcessFactory(
        private val failure: Throwable,
    ) : PtyProcessFactory {
        override fun start(request: PtyLaunchRequest): PtyProcess = throw failure
    }

    private class FakePtyProcess : PtyProcess() {
        override fun setWinSize(winSize: WinSize) {
            // no-op
        }

        override fun getWinSize(): WinSize = WinSize(80, 24)

        override fun destroy() {}

        override fun destroyForcibly(): Process = this

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf())

        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    }
}
