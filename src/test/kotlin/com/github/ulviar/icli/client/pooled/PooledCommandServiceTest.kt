package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.CommandResult
import com.github.ulviar.icli.client.LineSessionException
import com.github.ulviar.icli.client.ResponseDecoder
import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ProcessEngine
import com.github.ulviar.icli.engine.ProcessResult
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UncheckedIOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.text.Charsets

class PooledCommandServiceTest {
    private val engine = PoolingTestEngine()
    private val baseCommand =
        CommandDefinition
            .builder()
            .command("fake", "service")
            .build()
    private val options = ExecutionOptions.builder().idleTimeout(Duration.ZERO).build()
    private val scheduler = InlineScheduler()

    @AfterTest
    fun tearDown() {
        scheduler.close()
        engine.closeSessions()
    }

    @Test
    fun commandRunnerProcessesInputUsingPool() {
        engine.responder = { payload -> "reply:$payload" }

        val service = newService()
        service.commandRunner(PooledClientSpec.defaultSpec()).use { runner ->
            val result = runner.process("ping")

            assertTrue(result.success)
            assertEquals("reply:ping", result.value)
            assertEquals(listOf("ping"), engine.requestLog)
        }
    }

    @Test
    fun commandRunnerWithDecoderOverridesDefault() {
        engine.responder = { "ignored" }
        val decoderInvocations = AtomicInteger()
        val decoder =
            ResponseDecoder { _, _ ->
                decoderInvocations.incrementAndGet()
                "custom"
            }

        val service = newService()
        service.commandRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner.withDecoder(decoder).use { custom ->
                val result = custom.process("payload")

                assertTrue(result.success)
                assertEquals("custom", result.value)
            }
        }

        assertEquals(1, decoderInvocations.get())
    }

    @Test
    fun commandRunnerProcessAsyncDelegatesToScheduler() {
        engine.responder = { payload -> "async:$payload" }

        val service = newService()
        service.commandRunner(PooledClientSpec.defaultSpec()).use { runner ->
            val initial = scheduler.submissions
            val result = runner.processAsync("ping").join()

            assertTrue(result.success)
            assertEquals("async:ping", result.value)
            assertEquals(initial + 1, scheduler.submissions)
        }
    }

    @Test
    fun lineConversationProvidesProcessHelpersAndScope() {
        engine.responder = { payload -> "line:$payload" }

        val service = newService()
        service.lineSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner.open().use { conversation ->
                val result = conversation.process("one")

                assertTrue(result.success)
                assertEquals("line:one", result.value)
                assertNotNull(conversation.scope())

                conversation.reset()
            }
        }

        assertTrue(engine.sessionsCreated.get() >= 1)
    }

    @Test
    fun lineConversationProcessAsyncDelegatesToScheduler() {
        engine.responder = { payload -> "line:$payload" }

        val service = newService()
        service.lineSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner.open().use { conversation ->
                val initial = scheduler.submissions
                val result = conversation.processAsync("two").join()

                assertTrue(result.success)
                assertEquals("line:two", result.value)
                assertEquals(initial + 1, scheduler.submissions)
            }
        }
    }

    @Test
    fun lineSessionRunnerCustomDecoderApplies() {
        engine.responder = { payload -> "line:$payload" }
        val delegate = ResponseDecoder.lineDelimited()
        val decoder =
            ResponseDecoder { stdout, charset ->
                "custom:${delegate.read(stdout, charset)}"
            }

        val service = newService()
        service.lineSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner
                .open { builder -> builder.decoder(decoder) }
                .use { conversation ->
                    val result = conversation.process("payload")

                    assertTrue(result.success)
                    assertEquals("custom:line:payload", result.value)
                }
        }
    }

    @Test
    fun interactiveConversationExposesUnderlyingHandle() {
        engine.responder = { payload -> payload.uppercase() }

        val service = newService()
        service.interactiveSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            val conversation = runner.open()
            val session = engine.lastSession()

            assertSame(session, conversation.interactive().handle())
            conversation.close()
        }
    }

    @Test
    fun commandRunnerCustomListenerReceivesCallbacks() {
        engine.responder = { payload -> payload }
        val listener = RecordingListener()

        val service = newService()
        service
            .commandRunner { spec ->
                spec
                    .maxSize(2)
                    .listener(listener)
            }.use { runner ->
                val result = runner.process("data")

                assertTrue(result.success)
            }

        assertEquals(listOf("data"), listener.startedInputs)
    }

    @Test
    fun clientFactoryProducesAdvancedClient() {
        engine.responder = { payload -> payload.reversed() }
        val service = newService()

        val listener = RecordingListener()

        service
            .client { spec ->
                spec
                    .maxSize(1)
                    .listener(listener)
            }.use { client ->
                val result = client.serviceProcessor().process("abc")

                assertTrue(result.success)
                assertEquals("cba", result.value)
            }

        assertEquals(listOf("abc"), listener.startedInputs)
    }

    @Test
    fun commandServiceIntegrationDelegatesToFacade() {
        engine.responder = { payload -> payload.reversed() }
        val service =
            com.github.ulviar.icli.client
                .CommandService(engine, baseCommand, options, scheduler)
        val listener = RecordingListener()

        val pooled = service.pooled()
        pooled.commandRunner(PooledClientSpec.defaultSpec()).use { runner ->
            val result = runner.process("abc")
            assertTrue(result.success)
            assertEquals("cba", result.value)
        }

        pooled
            .client { spec ->
                spec
                    .maxSize(1)
                    .listener(listener)
            }.use { client ->
                val result = client.serviceProcessor().process("xyz")
                assertTrue(result.success)
                assertEquals("zyx", result.value)
            }

        assertEquals(listOf("xyz"), listener.startedInputs)
    }

    @Test
    fun pooledLineConversationRetireDisposesWorker() {
        engine.responder = { payload -> payload }

        val service = newService()
        service.lineSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner.open().use { conversation ->
                conversation.retire()
            }
        }

        assertTrue(engine.sessionsCreated.get() >= 1)
    }

    @Test
    fun interactiveConversationLineHelperRespectsCustomDecoder() {
        engine.responder = { payload -> "interactive:$payload" }
        val delegate = ResponseDecoder.lineDelimited()
        val decoder =
            ResponseDecoder { stdout, charset ->
                "custom:${delegate.read(stdout, charset)}"
            }

        val service = newService()
        service.interactiveSessionRunner(PooledClientSpec.defaultSpec()).use { runner ->
            runner
                .open { builder -> builder.decoder(decoder) }
                .use { conversation ->
                    val result = conversation.process("value")

                    assertTrue(result.success)
                    assertEquals("custom:interactive:value", result.value)
                }
        }
    }

    @Test
    fun pooledCommandRunnerPropagatesFailuresFromWorker() {
        engine.responder = { _ ->
            throw UncheckedIOException("boom", IOException("boom"))
        }

        val service = newService()
        service.commandRunner(PooledClientSpec.defaultSpec()).use { runner ->
            val result = runner.process("boom")

            assertFalse(result.success)
            val error = assertIs<LineSessionException>(result.error)
            assertEquals("boom", error.input())
        }
    }

    private fun newService(): PooledCommandService =
        PooledCommandService(
            engine,
            baseCommand,
            options,
            scheduler,
            ResponseDecoder.lineDelimited(),
        )

    private class PoolingTestEngine : ProcessEngine {
        var responder: (String) -> String = { value -> value }
        val requestLog = mutableListOf<String>()
        private val sessions = mutableListOf<FakeInteractiveSession>()
        val sessionsCreated = AtomicInteger()

        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult = throw UnsupportedOperationException("run() not expected")

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession {
            val session =
                FakeInteractiveSession { payload ->
                    requestLog += payload
                    responder(payload)
                }
            sessions += session
            sessionsCreated.incrementAndGet()
            return session
        }

        fun lastSession(): FakeInteractiveSession = sessions.lastOrNull() ?: error("no session created")

        fun closeSessions() {
            sessions.forEach(FakeInteractiveSession::close)
        }
    }

    private class FakeInteractiveSession(
        private val responder: (String) -> String,
    ) : InteractiveSession {
        private val stdinBuffer = StringBuilder()
        private val stdoutPipe = PipedInput()
        private val exitFuture = CompletableFuture.completedFuture(0)
        private var closed = false

        override fun stdin(): OutputStream =
            object : OutputStream() {
                override fun write(b: Int) {
                    if (b == '\n'.code) {
                        val payload = stdinBuffer.toString()
                        stdinBuffer.setLength(0)
                        val reply = responder(payload)
                        stdoutPipe.emit(reply)
                    } else {
                        stdinBuffer.append(b.toChar())
                    }
                }
            }

        override fun stdout(): InputStream = stdoutPipe

        override fun stderr(): InputStream = InputStream.nullInputStream()

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
    }

    private class PipedInput : InputStream() {
        private val buffer = java.util.concurrent.LinkedBlockingQueue<Int>()

        fun emit(value: String) {
            value.toByteArray(Charsets.UTF_8).forEach { buffer.put(it.toInt()) }
            buffer.put('\n'.code)
        }

        override fun read(): Int {
            val value = buffer.take()
            return value
        }

        override fun close() {
            buffer.put(-1)
        }
    }

    private class RecordingListener : ServiceProcessorListener {
        val startedInputs = mutableListOf<String>()

        override fun requestStarted(
            scope: LeaseScope,
            input: String,
        ) {
            startedInputs += input
        }

        override fun requestCompleted(
            scope: LeaseScope,
            result: CommandResult<String>,
        ) {}

        override fun requestFailed(
            scope: LeaseScope,
            error: Throwable,
        ) {}

        override fun conversationOpened(scope: LeaseScope) {}

        override fun conversationClosing(scope: LeaseScope) {}

        override fun conversationClosed(scope: LeaseScope) {}

        override fun conversationReset(scope: LeaseScope) {}
    }
}
