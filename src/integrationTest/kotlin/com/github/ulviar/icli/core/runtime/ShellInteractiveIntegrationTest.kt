package com.github.ulviar.icli.core.runtime

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.TerminalPreference
import com.github.ulviar.icli.testing.TestProcessCommand
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class ShellInteractiveIntegrationTest {
    private val engine = StandardProcessEngine()

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `pty-backed interactive process echoes commands`() {
        val spec =
            CommandDefinition
                .builder()
                .command(TestProcessCommand.command("--interactive"))
                .terminalPreference(TerminalPreference.REQUIRED)
                .build()

        val session =
            try {
                engine.startSession(spec, ExecutionOptions.builder().build())
            } catch (ex: UnsupportedOperationException) {
                Assumptions.assumeTrue(false, "PTY unavailable: ${ex.message}")
                return
            }

        session.use {
            val writer = OutputStreamWriter(it.stdin(), StandardCharsets.UTF_8)
            val collector = StreamCollector(it.stdout())

            val readyOutput = collector.consumeUntil("READY\n")
            assertTrue(readyOutput.contains("READY"))

            sendLine(writer, "ping")
            assertTrue(collector.consumeUntil("OUT:ping\n").contains("OUT:ping"))

            sendLine(writer, "pong")
            assertTrue(collector.consumeUntil("OUT:pong\n").contains("OUT:pong"))

            sendLine(writer, "exit")
            it.onExit().get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `pipe-based echo process still supports session api`() {
        val spec =
            CommandDefinition
                .builder()
                .command(TestProcessCommand.command("--interactive"))
                .terminalPreference(TerminalPreference.DISABLED)
                .build()

        val session = engine.startSession(spec, ExecutionOptions.builder().build())
        session.use {
            val writer = OutputStreamWriter(it.stdin(), StandardCharsets.UTF_8)
            val collector = StreamCollector(it.stdout())

            val readyOutput = collector.consumeUntil("READY\n")
            assertTrue(readyOutput.contains("READY"))

            sendLine(writer, "pipe-test")
            assertTrue(collector.consumeUntil("OUT:pipe-test\n").contains("OUT:pipe-test"))

            sendLine(writer, "exit")
            it.closeStdin()
            it.onExit().get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `pipe session supports concurrent stdin writes and stdout reads`() {
        assertConcurrentInteraction(TerminalPreference.DISABLED)
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `pty session supports concurrent stdin writes and stdout reads`() {
        try {
            assertConcurrentInteraction(TerminalPreference.REQUIRED)
        } catch (ex: UnsupportedOperationException) {
            Assumptions.assumeTrue(false, "PTY unavailable: ${ex.message}")
        }
    }

    private fun sendLine(
        writer: OutputStreamWriter,
        line: String,
    ) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    private fun assertConcurrentInteraction(preference: TerminalPreference) {
        val spec =
            CommandDefinition
                .builder()
                .command(TestProcessCommand.command("--interactive"))
                .terminalPreference(preference)
                .build()

        val session = engine.startSession(spec, ExecutionOptions.builder().build())
        session.use {
            val reader = BufferedReader(InputStreamReader(it.stdout(), StandardCharsets.UTF_8))
            val writer = OutputStreamWriter(it.stdin(), StandardCharsets.UTF_8)
            val readyLatch = CountDownLatch(1)
            val outputs = CopyOnWriteArrayList<String>()
            val linesToSend = listOf("ping", "pong", "pang")
            val stopMarker = "OUT:exit"

            val readerFuture =
                CompletableFuture.runAsync {
                    while (true) {
                        val rawLine = reader.readLine() ?: break
                        val line = rawLine.replace("\r", "")
                        outputs += line
                        if (line == "READY") {
                            readyLatch.countDown()
                        }
                        if (line == stopMarker) {
                            break
                        }
                    }
                }

            val writerFuture =
                CompletableFuture.runAsync {
                    if (!readyLatch.await(5, TimeUnit.SECONDS)) {
                        fail("Timed out waiting for READY prompt")
                    }
                    linesToSend.forEach { line ->
                        sendLine(writer, line)
                    }
                    sendLine(writer, "exit")
                }

            CompletableFuture.allOf(readerFuture, writerFuture).get(10, TimeUnit.SECONDS)
            it.onExit().get(5, TimeUnit.SECONDS)

            assertTrue(outputs.contains("READY"), "Did not observe READY line: $outputs")
            linesToSend.forEach { expected ->
                assertTrue(outputs.contains("OUT:$expected"), "Missing OUT:$expected in $outputs")
            }
            assertTrue(outputs.contains(stopMarker), "Missing $stopMarker in $outputs")
        }
    }

    private class StreamCollector(
        private val input: InputStream,
    ) {
        private val buffer = StringBuilder()
        private val charset = StandardCharsets.UTF_8

        fun consumeUntil(
            marker: String,
            timeout: Duration = Duration.ofSeconds(5),
        ): String {
            val deadline = System.nanoTime() + timeout.toMillis() * 1_000_000
            val bytes = ByteArray(1024)
            while (System.nanoTime() < deadline) {
                val read =
                    try {
                        CompletableFuture
                            .supplyAsync { input.read(bytes) }
                            .get(200, TimeUnit.MILLISECONDS)
                    } catch (ex: TimeoutException) {
                        null
                    }

                if (read == null) {
                    continue
                }

                if (read <= 0) {
                    break
                }

                val chunk = String(bytes, 0, read, charset).replace("\r", "")
                buffer.append(chunk)
                val index = buffer.indexOf(marker)
                if (index >= 0) {
                    val result = buffer.substring(0, index + marker.length)
                    buffer.delete(0, index + marker.length)
                    return result
                }
            }
            fail("Timed out waiting for '$marker'. Accumulated output: $buffer")
        }
    }
}
