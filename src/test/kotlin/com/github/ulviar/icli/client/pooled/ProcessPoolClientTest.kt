package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.CommandService
import com.github.ulviar.icli.client.LineSessionException
import com.github.ulviar.icli.client.ResponseDecoder
import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ProcessEngine
import com.github.ulviar.icli.engine.ProcessResult
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.pool.api.hooks.RequestTimeoutScheduler
import com.github.ulviar.icli.engine.pool.api.hooks.ResetHook
import com.github.ulviar.icli.engine.pool.api.hooks.ResetOutcome
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProcessPoolClientTest {
    private val engine = FakeProcessEngine()
    private val scheduler = InlineScheduler()
    private val diagnostics = RecordingDiagnostics()
    private val resetHook = RecordingResetHook()

    private val baseCommand =
        CommandDefinition
            .builder()
            .command("fake", "command")
            .build()

    private val baseOptions =
        ExecutionOptions
            .builder()
            .idleTimeout(Duration.ZERO)
            .build()

    private fun newConfig(): ProcessPoolConfig =
        ProcessPoolConfig
            .builder(baseCommand)
            .workerOptions(baseOptions)
            .minSize(0)
            .maxSize(1)
            .requestTimeout(Duration.ofSeconds(5))
            .diagnosticsListener(diagnostics)
            .addResetHook(resetHook)
            .build()

    @AfterTest
    fun tearDown() {
        scheduler.close()
    }

    @Test
    fun `service processor processes line requests`() {
        engine.response = { payload -> "echo:$payload" }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val result = client.serviceProcessor().process("ping")

                assertTrue(result.success)
                assertEquals("echo:ping", result.value)
                assertEquals(listOf("ping"), engine.requestLog)
                assertEquals(1, resetHook.completed.size)
                assertEquals(1, diagnostics.leaseAcquired.get())
                assertEquals(1, diagnostics.leaseReleased.get())
            }
    }

    @Test
    fun `async processing delegates work to scheduler`() {
        engine.response = { payload -> payload.uppercase() }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val result = client.serviceProcessor().processAsync("async").join()

                assertTrue(result.success)
                assertEquals("ASYNC", result.value)
                assertEquals(1, scheduler.submissions)
            }
    }

    @Test
    fun `service processor creations are independent`() {
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val first = client.serviceProcessor()
                val second = client.serviceProcessor()

                assertNotSame(first, second)
            }
    }

    @Test
    fun `service processor with decoder overrides default`() {
        engine.response = { "raw" }
        val decoderInvocations = AtomicInteger()
        val decoder =
            ResponseDecoder { _, _ ->
                decoderInvocations.incrementAndGet()
                "decoded"
            }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val result = client.serviceProcessor(decoder).process("payload")

                assertTrue(result.success)
                assertEquals("decoded", result.value)
            }

        assertEquals(1, decoderInvocations.get())
    }

    @Test
    fun `processor returns failure when responder aborts`() {
        engine.response = { _ -> error("boom") }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val result = client.serviceProcessor().process("broken")

                assertFalse(result.success)
                assertEquals("broken", (result.error as LineSessionException).input())
                assertEquals(1, resetHook.manual.size)
            }
    }

    @Test
    fun `conversation keeps lease until closed`() {
        engine.response = { payload -> "[$payload]" }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                client.openConversation().use { conversation ->
                    val first = conversation.line().process("one")
                    val second = conversation.line().process("two")

                    assertEquals("[one]", first.value)
                    assertEquals("[two]", second.value)
                    assertEquals(1, engine.sessionsCreated.get())
                    assertEquals(listOf("one", "two"), engine.requestLog)
                }

                val result = client.serviceProcessor().process("three")
                assertEquals("[three]", result.value)
                assertEquals(listOf("one", "two", "three"), engine.requestLog)
                assertEquals(1, engine.sessionsCreated.get())
                assertEquals(2, resetHook.completed.size) // conversation close + stateless request
            }
    }

    @Test
    fun `command service forwards defaults into pooled client`() {
        engine.response = { payload -> payload }

        val service = CommandService(engine, baseCommand, baseOptions, scheduler)
        service
            .pooled()
            .client { spec ->
                spec
                    .minSize(1)
                    .requestTimeout(Duration.ofSeconds(2))
            }.use { client ->
                val result = client.serviceProcessor().process("from-service")
                assertTrue(result.success)
            }

        assertTrue(engine.sessionsCreated.get() >= 1)
        assertEquals(listOf("from-service"), engine.requestLog)
    }

    @Test
    fun `listener receives request and conversation events`() {
        val listener = RecordingListener()
        engine.response = { payload ->
            if (payload == "fail") {
                error("boom")
            }
            payload.lowercase()
        }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                listener,
            ).use { client ->
                val success = client.serviceProcessor().process("OK")
                assertTrue(success.success)

                val failure = client.serviceProcessor().process("fail")
                assertFalse(failure.success)

                client.openConversation().use { conversation ->
                    conversation.reset()
                    val convo = conversation.line().process("CHAT")
                    assertTrue(convo.success)
                }
            }

        assertEquals(listOf("OK", "fail"), listener.startedInputs)
        assertEquals(1, listener.completed.size)
        assertEquals(1, listener.failures.size)
        assertEquals(1, listener.conversationOpenedCount)
        assertEquals(1, listener.conversationClosingCount)
        assertEquals(1, listener.conversationResetCount)
        assertEquals(1, listener.conversationClosedCount)
    }

    @Test
    fun `conversation affinity reuses worker when available`() {
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val affinity = ConversationAffinity.key("chat-1")
                val first = client.openConversation(affinity)
                val workerId = first.scope().workerId()
                first.close()

                val second = client.openConversation(affinity)
                assertEquals(workerId, second.scope().workerId())
                second.close()
            }
    }

    @Test
    fun `retiring conversation clears affinity cache`() {
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            ).use { client ->
                val affinity = ConversationAffinity.key("chat-2")
                val first = client.openConversation(affinity)
                val originalWorker = first.scope().workerId()
                first.retire(ConversationRetirement.unhealthy("state drift"))

                val second = client.openConversation(affinity)
                assertNotEquals(originalWorker, second.scope().workerId())
                second.close()
            }
    }

    @Test
    fun `listener receives retirement metadata`() {
        val listener = RecordingListener()
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                listener,
            ).use { client ->
                client.openConversation().retire(ConversationRetirement.unhealthy("diagnostic failure"))
            }

        assertEquals(listOf("diagnostic failure"), listener.retirements.map { it.reason() })
    }

    @Test
    fun `withListener returns original instance when listener matches`() {
        val listener = RecordingListener()
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                listener,
            ).use { client ->
                val same = client.withListener(listener)

                assertSame(client, same)
                val result = same.serviceProcessor().process("echo")
                assertTrue(result.success)
                assertEquals("echo", result.value)
            }
    }

    @Test
    fun `retiring conversation disposes worker`() {
        val listener = RecordingListener()
        engine.response = { payload -> payload }

        ProcessPoolClient
            .create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                listener,
            ).use { client ->
                client.openConversation().retire()
                val result = client.serviceProcessor().process("after")

                assertTrue(result.success, "expected pooled request to succeed after retiring worker: ${result.error}")
            }

        val created = engine.sessionsCreated.get()
        assertTrue(created >= 2, "expected at least two sessions after retirement, observed $created")
        assertEquals(listOf("after"), engine.requestLog.takeLast(1))
        assertEquals(1, listener.conversationOpenedCount)
        assertEquals(1, listener.conversationClosedCount)
    }

    @Test
    fun `close shuts down pool and prevents further leases`() {
        engine.response = { payload -> payload }

        val client =
            ProcessPoolClient.create(
                engine,
                newConfig(),
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            )
        client.close()

        assertFailsWith<com.github.ulviar.icli.engine.pool.api.ServiceUnavailableException> {
            client.pool().acquire()
        }
    }

    @Test
    fun `close throws when pool cannot drain in time`() {
        engine.response = { payload -> payload }
        val timeoutScheduler = HangingTimeoutScheduler()
        val config =
            ProcessPoolConfig
                .builder(baseCommand)
                .workerOptions(baseOptions)
                .minSize(0)
                .maxSize(1)
                .requestTimeout(Duration.ofMillis(50))
                .requestTimeoutSchedulerFactory { timeoutScheduler }
                .diagnosticsListener(diagnostics)
                .addResetHook(resetHook)
                .build()
        val client =
            ProcessPoolClient.create(
                engine,
                config,
                scheduler,
                ResponseDecoder.lineDelimited(),
                ServiceProcessorListener.noOp(),
            )

        val lease = client.pool().acquire(Duration.ofSeconds(1))

        val thrown =
            assertFailsWith<IllegalStateException> {
                client.close()
            }
        assertTrue(thrown.message!!.contains("unable to drain"))

        lease.close()
        client.pool().drain(Duration.ofSeconds(1))
    }

    private class RecordingDiagnostics : PoolDiagnosticsListener {
        val leaseAcquired = AtomicInteger()
        val leaseReleased = AtomicInteger()

        override fun leaseAcquired(workerId: Int) {
            leaseAcquired.incrementAndGet()
        }

        override fun leaseReleased(workerId: Int) {
            leaseReleased.incrementAndGet()
        }
    }

    private class RecordingResetHook : ResetHook {
        val completed = mutableListOf<UUID>()
        val manual = mutableListOf<UUID>()

        override fun reset(
            session: InteractiveSession,
            scope: LeaseScope,
            request: ResetRequest,
        ): ResetOutcome {
            when (request.reason()) {
                ResetRequest.Reason.LEASE_COMPLETED -> {
                    completed.add(request.requestId())
                    return ResetOutcome.CONTINUE
                }

                ResetRequest.Reason.MANUAL -> {
                    manual.add(request.requestId())
                    return ResetOutcome.RETIRE
                }

                ResetRequest.Reason.TIMEOUT,
                ResetRequest.Reason.CLIENT_RETIRE,
                -> return ResetOutcome.RETIRE
            }
        }
    }

    private class HangingTimeoutScheduler : RequestTimeoutScheduler {
        override fun schedule(
            workerId: Int,
            requestId: UUID,
            timeout: Duration,
            onTimeout: Runnable,
        ) {
            // Intentionally never executes the callback so active leases remain outstanding.
        }

        override fun cancel(workerId: Int) {}

        override fun complete(
            workerId: Int,
            requestId: UUID,
        ): Boolean = false

        override fun close() {}
    }

    private class FakeProcessEngine : ProcessEngine {
        val sessionsCreated = AtomicInteger()
        val requestLog = mutableListOf<String>()

        var response: (String) -> String = { throw IllegalStateException("response not configured") }

        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult = throw UnsupportedOperationException("run() not expected")

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession {
            sessionsCreated.incrementAndGet()
            return FakeInteractiveSession { payload ->
                requestLog.add(payload)
                response(payload)
            }
        }
    }
}
