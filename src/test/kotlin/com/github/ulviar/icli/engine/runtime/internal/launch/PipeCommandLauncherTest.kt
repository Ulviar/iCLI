package com.github.ulviar.icli.engine.runtime.internal.launch

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ShellConfiguration
import com.github.ulviar.icli.engine.TerminalPreference
import java.nio.file.Path
import java.util.Map
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PipeCommandLauncherTest {
    @Test
    fun `builds command line with shell when configured`() {
        val starter = RecordingProcessStarter()
        val launcher = PipeCommandLauncher(starter)
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .shell(ShellConfiguration.builder().command("/bin/sh", "-c").build())
                .putEnvironment("FOO", "bar")
                .workingDirectory(Path.of("/tmp"))
                .build()

        launcher.launch(spec, false)

        val invocation = starter.lastInvocation.get()
        assertEquals(listOf("/bin/sh", "-c", "echo", "hi"), invocation?.commandLine)
        assertEquals(Path.of("/tmp"), invocation?.workingDirectory)
        assertEquals("bar", invocation?.environment?.get("FOO"))
        assertEquals(false, invocation?.redirectErrorStream)
    }

    @Test
    fun `honours redirect error stream flag`() {
        val starter = RecordingProcessStarter()
        val launcher = PipeCommandLauncher(starter)
        val spec = CommandDefinition.builder().command(listOf("echo", "hi")).build()

        launcher.launch(spec, true)

        val invocation = starter.lastInvocation.get()
        assertEquals(true, invocation?.redirectErrorStream)
    }

    @Test
    fun `rejects required PTY`() {
        val launcher = PipeCommandLauncher(RecordingProcessStarter())
        val spec =
            CommandDefinition
                .builder()
                .command(listOf("echo", "hi"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        assertFailsWith<UnsupportedOperationException> {
            launcher.launch(spec, false)
        }
    }

    private class RecordingProcessStarter : ProcessStarter {
        val lastInvocation: AtomicReference<Invocation?> = AtomicReference(null)

        override fun start(
            commandLine: MutableList<String>,
            workingDirectory: java.nio.file.Path?,
            environment: MutableMap<String, String>,
            redirectErrorStream: Boolean,
        ): Process {
            lastInvocation.set(Invocation(commandLine, workingDirectory, environment, redirectErrorStream))
            return FakeProcess()
        }
    }

    private data class Invocation(
        val commandLine: List<String>,
        val workingDirectory: java.nio.file.Path?,
        val environment: MutableMap<String, String>,
        val redirectErrorStream: Boolean,
    )

    private class FakeProcess : Process() {
        override fun destroy() {}

        override fun exitValue(): Int = 0

        override fun getOutputStream(): java.io.OutputStream = throw UnsupportedOperationException()

        override fun getInputStream(): java.io.InputStream = throw UnsupportedOperationException()

        override fun getErrorStream(): java.io.InputStream = throw UnsupportedOperationException()

        override fun waitFor(): Int = 0
    }
}
