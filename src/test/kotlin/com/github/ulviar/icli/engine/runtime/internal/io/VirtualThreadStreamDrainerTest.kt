package com.github.ulviar.icli.engine.runtime.internal.io

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VirtualThreadStreamDrainerTest {
    @Test
    fun `drains entire stream asynchronously`() {
        val drainer = VirtualThreadStreamDrainer()
        val sink = InMemorySink()

        val future = drainer.drain(ByteArrayInputStream("hello".toByteArray()), sink)
        future.join()

        assertEquals("hello", sink.content())
        assertTrue(sink.appendCount >= 1)
        drainer.close()
    }

    @Test
    fun `closes source stream after drain`() {
        val drainer = VirtualThreadStreamDrainer()
        val stream = TrackingInputStream("hi".toByteArray())

        drainer.drain(stream, InMemorySink()).join()

        assertTrue(stream.closed)
        drainer.close()
    }

    @Test
    fun `propagates io failure`() {
        val drainer = VirtualThreadStreamDrainer()
        val sink = InMemorySink()

        val future = drainer.drain(FailingInputStream(), sink)

        val thrown = runCatching { future.join() }.exceptionOrNull()
        assertTrue(thrown is RuntimeException)
        drainer.close()
    }

    @Test
    fun `rejects submissions after close`() {
        val drainer = VirtualThreadStreamDrainer()
        drainer.close()

        assertFailsWith<RejectedExecutionException> {
            drainer.drain(ByteArrayInputStream("noop".toByteArray()), InMemorySink())
        }
    }

    private class InMemorySink : OutputSink {
        private val builder = StringBuilder()
        var appendCount: Int = 0
            private set

        override fun append(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            appendCount++
            builder.append(String(buffer, offset, length, Charsets.UTF_8))
        }

        override fun content(): String = builder.toString()
    }

    private class FailingInputStream : ByteArrayInputStream(ByteArray(0)) {
        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = throw IOException("boom")
    }

    private class TrackingInputStream(
        bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
