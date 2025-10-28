package com.github.ulviar.icli.testing

import com.github.ulviar.icli.core.InteractiveSession
import com.github.ulviar.icli.core.ShutdownSignal
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture

class ScriptedInteractiveSession : InteractiveSession {
    private val buffer = ByteArrayOutputStream()
    private val stdinStream =
        object : OutputStream() {
            override fun write(b: Int) {
                buffer.write(b)
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                buffer.write(b, off, len)
            }

            override fun flush() {
                // no-op
            }

            override fun close() {
                stdinClosed = true
            }
        }

    private val stdoutStream = ScriptedInputStream()
    private val stderrStream = ScriptedInputStream()
    private val exitFuture = CompletableFuture.completedFuture(0)

    var stdinClosed: Boolean = false
        private set
    var closed: Boolean = false
        private set
    var lastSignal: ShutdownSignal? = null
        private set
    var lastResize: Pair<Int, Int>? = null
        private set

    override fun stdin(): OutputStream = stdinStream

    override fun stdout(): InputStream = stdoutStream

    override fun stderr(): InputStream = stderrStream

    override fun onExit(): CompletableFuture<Int> = exitFuture

    override fun closeStdin() {
        stdinClosed = true
    }

    override fun sendSignal(signal: ShutdownSignal) {
        lastSignal = signal
    }

    override fun resizePty(
        columns: Int,
        rows: Int,
    ) {
        lastResize = columns to rows
    }

    override fun close() {
        closed = true
    }

    fun consumedInput(): String = buffer.toString(StandardCharsets.UTF_8)

    fun queueResponse(value: String) {
        stdoutStream.enqueue(value)
    }

    private class ScriptedInputStream : InputStream() {
        private val queue: ArrayDeque<ByteArray> = ArrayDeque()
        private var current: ByteArray = ByteArray(0)
        private var index: Int = 0

        fun enqueue(value: String) {
            val bytes = (value + "\n").toByteArray(StandardCharsets.UTF_8)
            queue.addLast(bytes)
        }

        override fun read(): Int {
            while (index >= current.size) {
                if (queue.isEmpty()) {
                    return -1
                }
                current = queue.removeFirst()
                index = 0
            }
            return current[index++].toInt() and 0xFF
        }
    }
}
