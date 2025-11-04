package com.github.ulviar.icli.testing

import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * Test-only interactive session that exposes writable stdout convenience helpers for streaming scenarios.
 */
class StreamingTestSession : InteractiveSession {
    private val stdoutPipe = Pipe()
    private val stderrPipe = Pipe()
    private val exitFuture = CompletableFuture.completedFuture(0)
    var closed: Boolean = false
        private set

    override fun stdin(): OutputStream = OutputStream.nullOutputStream()

    override fun stdout(): InputStream = stdoutPipe.input

    override fun stderr(): InputStream = stderrPipe.input

    override fun onExit(): CompletableFuture<Int> = exitFuture

    override fun closeStdin() {}

    override fun sendSignal(signal: ShutdownSignal) {}

    override fun resizePty(
        columns: Int,
        rows: Int,
    ) {}

    override fun close() {
        closed = true
        stdoutPipe.close()
    }

    fun emitStdout(value: String) {
        stdoutPipe.write(value)
    }

    fun closeStdout() {
        stdoutPipe.close()
    }

    fun emitStderr(value: String) {
        stderrPipe.write(value)
    }

    fun closeStderr() {
        stderrPipe.close()
    }

    private class Pipe {
        private val output = PipedOutputStream()
        val input: PipedInputStream = PipedInputStream(output, 8192)

        fun write(value: String) {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }

        fun close() {
            output.close()
        }
    }
}
