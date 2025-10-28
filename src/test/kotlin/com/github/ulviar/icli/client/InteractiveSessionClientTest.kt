package com.github.ulviar.icli.client

import com.github.ulviar.icli.core.InteractiveSession
import com.github.ulviar.icli.core.ShutdownSignal
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class InteractiveSessionClientTest {
    @Test
    fun `sendLine writes payload newline and flushes with default charset`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        client.sendLine("ping")

        assertEquals("ping\n", session.stdin.content(StandardCharsets.UTF_8))
        assertEquals(1, session.stdin.flushCount)
    }

    @Test
    fun `sendLine honours custom charset`() {
        val session = RecordingInteractiveSession()
        val charset = StandardCharsets.ISO_8859_1
        val client = InteractiveSessionClient.wrap(session, charset)

        client.sendLine("café")

        assertEquals("café\n", session.stdin.content(charset))
    }

    @Test
    fun `sendLine wraps IO failures`() {
        val client =
            InteractiveSessionClient.wrap(
                object : InteractiveSession {
                    override fun stdin(): OutputStream =
                        object : OutputStream() {
                            override fun write(b: Int): Unit = throw IOException("boom")
                        }

                    override fun stdout(): InputStream = ByteArrayInputStream(ByteArray(0))

                    override fun stderr(): InputStream = ByteArrayInputStream(ByteArray(0))

                    override fun onExit(): CompletableFuture<Int> = CompletableFuture.completedFuture(0)

                    override fun closeStdin() = fail("not expected")

                    override fun sendSignal(signal: ShutdownSignal) = fail("not expected")

                    override fun resizePty(
                        columns: Int,
                        rows: Int,
                    ) = fail("not expected")

                    override fun close() = fail("not expected")
                },
            )

        val error =
            kotlin
                .runCatching {
                    client.sendLine("fail")
                }.exceptionOrNull()
        assertTrue(error is UncheckedIOException)
        assertTrue(error.cause is IOException)
    }

    @Test
    fun `closeStdin delegates to session`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        client.closeStdin()

        assertEquals(1, session.closeStdinCount)
    }

    @Test
    fun `resizePty delegates to session`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        client.resizePty(200, 40)

        assertEquals(listOf(200 to 40), session.resizes)
    }

    @Test
    fun `sendSignal delegates to session`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        client.sendSignal(ShutdownSignal.KILL)

        assertEquals(listOf(ShutdownSignal.KILL), session.signals)
    }

    @Test
    fun `close delegates to session`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        client.close()

        assertTrue(session.closed.get())
    }

    @Test
    fun `exposes underlying streams and future`() {
        val session = RecordingInteractiveSession()
        val client = InteractiveSessionClient.wrap(session)

        assertSame(session.stdin, client.stdin())
        assertSame(session.stdout, client.stdout())
        assertSame(session.stderr, client.stderr())
        assertSame(session.exitFuture, client.onExit())
    }

    @Test
    fun `reports configured charset`() {
        val session = RecordingInteractiveSession()
        val charset = Charset.forName("UTF-16LE")
        val client = InteractiveSessionClient.wrap(session, charset)

        assertSame(charset, client.charset())
    }

    private class RecordingInteractiveSession : InteractiveSession {
        val stdin = RecordingOutputStream()
        val stdout: InputStream = ByteArrayInputStream(ByteArray(0))
        val stderr: InputStream = ByteArrayInputStream(ByteArray(0))
        val exitFuture: CompletableFuture<Int> = CompletableFuture.completedFuture(0)
        val signals: MutableList<ShutdownSignal> = mutableListOf()
        val resizes: MutableList<Pair<Int, Int>> = mutableListOf()
        val closed: AtomicBoolean = AtomicBoolean(false)
        var closeStdinCount: Int = 0

        override fun stdin(): OutputStream = stdin

        override fun stdout(): InputStream = stdout

        override fun stderr(): InputStream = stderr

        override fun onExit(): CompletableFuture<Int> = exitFuture

        override fun closeStdin() {
            closeStdinCount += 1
        }

        override fun sendSignal(signal: ShutdownSignal) {
            signals += signal
        }

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {
            resizes += columns to rows
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class RecordingOutputStream : OutputStream() {
        private val buffer = mutableListOf<Byte>()
        var flushCount: Int = 0
            private set

        override fun write(b: Int) {
            buffer += (b and 0xFF).toByte()
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            for (index in 0 until len) {
                buffer += b[off + index]
            }
        }

        override fun flush() {
            flushCount += 1
        }

        fun content(charset: Charset): String = buffer.toByteArray().toString(charset)
    }
}
