package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.ClientScheduler
import com.github.ulviar.icli.client.CommandResult
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class InlineScheduler : ClientScheduler {
    var submissions: Int = 0

    override fun <T> submit(task: Callable<T>): CompletableFuture<T> {
        submissions += 1
        return try {
            CompletableFuture.completedFuture(task.call())
        } catch (ex: Exception) {
            CompletableFuture<T>().apply { completeExceptionally(ex) }
        }
    }

    override fun close() {}
}

class FakeInteractiveSession(
    private val responder: (String) -> String,
) : InteractiveSession {
    private val stdoutQueue = LinkedBlockingQueue<Int>()
    private val exitFuture = CompletableFuture<Int>()
    private val currentLine = StringBuilder()
    private val closed = AtomicBoolean()

    override fun stdin(): OutputStream =
        object : OutputStream() {
            override fun write(b: Int) {
                if (b == '\n'.code) {
                    val payload = currentLine.toString()
                    currentLine.setLength(0)
                    try {
                        val reply = responder(payload)
                        emitLine(reply)
                    } catch (ex: Exception) {
                        emitFailure(ex)
                    }
                } else {
                    currentLine.append(b.toChar())
                }
            }
        }

    override fun stdout(): InputStream =
        object : InputStream() {
            override fun read(): Int = stdoutQueue.take()
        }

    override fun stderr(): InputStream = InputStream.nullInputStream()

    override fun onExit(): CompletableFuture<Int> = exitFuture

    override fun closeStdin() {
        // no-op for fake
    }

    override fun sendSignal(signal: ShutdownSignal) {
        // no-op
    }

    override fun resizePty(
        columns: Int,
        rows: Int,
    ) {
        // no-op
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            exitFuture.complete(0)
            stdoutQueue.put(-1)
        }
    }

    fun isClosed(): Boolean = closed.get()

    private fun emitLine(reply: String) {
        reply.toByteArray(Charsets.UTF_8).forEach { stdoutQueue.put(it.toInt()) }
        stdoutQueue.put('\n'.code)
    }

    private fun emitFailure(cause: Exception) {
        stdoutQueue.put(-1)
        if (cause is UncheckedIOException) {
            throw cause
        }
        throw UncheckedIOException("Responder failed", cause as? IOException ?: IOException(cause))
    }
}

class RecordingListener : ServiceProcessorListener {
    val startedInputs = mutableListOf<String>()
    val completed = mutableListOf<LeaseScope>()
    val failures = mutableListOf<Throwable>()
    var conversationOpenedCount = 0
    var conversationClosingCount = 0
    var conversationClosedCount = 0
    var conversationResetCount = 0
    val resetSignals = mutableListOf<ConversationReset>()
    val retirements = mutableListOf<ConversationRetirement>()

    override fun requestStarted(
        scope: LeaseScope,
        input: String,
    ) {
        startedInputs += input
    }

    override fun requestCompleted(
        scope: LeaseScope,
        result: CommandResult<String>,
    ) {
        completed += scope
    }

    override fun requestFailed(
        scope: LeaseScope,
        error: Throwable,
    ) {
        failures += error
    }

    override fun conversationOpened(scope: LeaseScope) {
        conversationOpenedCount += 1
    }

    override fun conversationClosing(scope: LeaseScope) {
        conversationClosingCount += 1
    }

    override fun conversationClosed(scope: LeaseScope) {
        conversationClosedCount += 1
    }

    override fun conversationReset(
        scope: LeaseScope,
        reset: ConversationReset,
    ) {
        conversationResetCount += 1
        resetSignals += reset
    }

    override fun conversationRetired(
        scope: LeaseScope,
        retirement: ConversationRetirement,
    ) {
        retirements += retirement
    }
}
