package com.github.ulviar.icli.client

import com.github.ulviar.icli.testing.ImmediateClientScheduler
import com.github.ulviar.icli.testing.ScriptedInteractiveSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LineExpectTest {
    private lateinit var sessionHandle: ScriptedInteractiveSession
    private val toClose: MutableList<ClientScheduler> = mutableListOf()

    @BeforeTest
    fun setUp() {
        sessionHandle = ScriptedInteractiveSession()
    }

    @AfterTest
    fun tearDown() {
        toClose.forEach { it.close() }
        toClose.clear()
    }

    @Test
    fun `expect helper consumes prompt and sends commands`() {
        val scheduler = ImmediateClientScheduler()
        val client = newClient(sessionHandle, scheduler)
        sessionHandle.queueResponse(">>> ")

        client.use { session ->
            val expect =
                session
                    .expect()
                    .withDefaultTimeout(Duration.ZERO)

            val prompt = expect.expectLine(">>> ")
            assertEquals(">>> ", prompt)

            sessionHandle.queueResponse("42")
            sessionHandle.queueResponse(">>> ")

            expect.send("print(6 * 7)")
            assertEquals("42", expect.expectLine("42"))
            assertEquals(">>> ", expect.expectLine(">>> "))

            assertEquals("print(6 * 7)\n", sessionHandle.consumedInput())
        }
    }

    @Test
    fun `expectAny returns response without validation`() {
        val scheduler = ImmediateClientScheduler()
        val client = newClient(sessionHandle, scheduler)
        sessionHandle.queueResponse("{\"analysis\":\"кот\"}")
        sessionHandle.queueResponse(">")

        client.use { session ->
            val expect = session.expect().withDefaultTimeout(Duration.ofSeconds(1))

            val payload = expect.expectAny()
            assertEquals("{\"analysis\":\"кот\"}", payload)
            assertEquals(">", expect.expectLine(">"))
        }
    }

    @Test
    fun `expect helper reports mismatched output`() {
        val scheduler = ImmediateClientScheduler()
        val client = newClient(sessionHandle, scheduler)
        sessionHandle.queueResponse("ready")

        client.use { session ->
            val expect = session.expect().withDefaultTimeout(Duration.ZERO)

            val error =
                assertFailsWith<LineExpectationException> {
                    expect.expectLine("different")
                }

            assertTrue(error.message!!.contains("different"))
            assertTrue(error.message!!.contains("ready"))
        }
    }

    @Test
    fun `expect helper times out when output absent`() {
        val scheduler = trackScheduler(ClientSchedulers.virtualThreads())
        val blockingSession = BlockingInteractiveSession()
        val client =
            LineSessionClient.create(
                InteractiveSessionClient.wrap(blockingSession),
                LineDelimitedResponseDecoder(),
                scheduler,
            )

        client.use { session ->
            val expect =
                session
                    .expect()
                    .withDefaultTimeout(Duration.ofMillis(50))

            assertFailsWith<LineExpectationTimeoutException> {
                expect.expectLine("welcome")
            }
        }

        blockingSession.close()
    }

    private fun newClient(
        session: ScriptedInteractiveSession,
        scheduler: ClientScheduler,
    ): LineSessionClient =
        LineSessionClient.create(InteractiveSessionClient.wrap(session), LineDelimitedResponseDecoder(), scheduler)

    private fun trackScheduler(scheduler: ClientScheduler): ClientScheduler {
        toClose += scheduler
        return scheduler
    }

    private class BlockingInteractiveSession : com.github.ulviar.icli.engine.InteractiveSession {
        private val stdoutPipe = PipedInputStream()
        private val stdoutSink = PipedOutputStream(stdoutPipe)
        private val stdinBuffer = ByteArrayOutputStream()
        private val exitFuture = CompletableFuture.completedFuture(0)

        override fun stdin(): OutputStream =
            object : OutputStream() {
                override fun write(b: Int) {
                    stdinBuffer.write(b)
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    stdinBuffer.write(b, off, len)
                }

                override fun flush() {
                    // no-op
                }

                override fun close() {
                    // no-op
                }
            }

        override fun stdout(): InputStream = stdoutPipe

        override fun stderr(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun onExit(): CompletableFuture<Int> = exitFuture

        override fun closeStdin() {
            // no-op
        }

        override fun sendSignal(signal: com.github.ulviar.icli.engine.ShutdownSignal) {
            // no-op
        }

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {
            // no-op
        }

        override fun close() {
            stdoutSink.close()
            stdoutPipe.close()
        }
    }
}
