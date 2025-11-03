package com.github.ulviar.icli.client

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.WorkerLease
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceProcessorTest {
    @Test
    fun `process returns success and closes lease`() {
        val lease = FakeWorkerLease { payload -> "reply:$payload" }
        val scheduler = InlineScheduler()
        val listener = RecordingListener()
        val processor = ServiceProcessor({ lease }, scheduler, LineDelimitedResponseDecoder(), listener)

        val result = processor.process("ping")

        assertTrue(result.success)
        assertEquals("reply:ping", result.value)
        assertEquals(1, lease.closeCount.get())
        assertEquals(0, lease.manualResetCount.get())
        assertEquals(listOf("ping"), listener.startedInputs)
        assertEquals(1, listener.completed.size)
        assertTrue(listener.failures.isEmpty())
    }

    @Test
    fun `process propagates failure and resets lease`() {
        val failure = java.io.UncheckedIOException("boom", java.io.IOException("boom"))
        val lease = FakeWorkerLease { throw failure }
        val scheduler = InlineScheduler()
        val listener = RecordingListener()
        val processor = ServiceProcessor({ lease }, scheduler, LineDelimitedResponseDecoder(), listener)

        val result = processor.process("fail")

        assertFalse(result.success)
        assertEquals(1, lease.manualResetCount.get())
        assertEquals(1, listener.failures.size)
        assertEquals(1, lease.closeCount.get())
    }

    @Test
    fun `process async delegates to scheduler`() {
        val lease = FakeWorkerLease { payload -> payload.uppercase() }
        val scheduler = InlineScheduler()
        val listener = RecordingListener()
        val processor = ServiceProcessor({ lease }, scheduler, LineDelimitedResponseDecoder(), listener)

        val result = processor.processAsync("async").join()

        assertTrue(result.success)
        assertEquals("ASYNC", result.value)
        assertEquals(1, scheduler.submissions)
    }

    @Test
    fun `process rethrows exceptions after notifying listener`() {
        val lease = FakeWorkerLease { payload -> "reply:$payload" }
        val scheduler = InlineScheduler()
        val listener = RecordingListener()
        val decoder =
            ResponseDecoder { _, _ -> throw IllegalStateException("decoder boom") }
        val processor = ServiceProcessor({ lease }, scheduler, decoder, listener)

        val thrown =
            kotlin.test.assertFailsWith<IllegalStateException> {
                processor.process("explode")
            }

        assertEquals("decoder boom", thrown.message)
        assertEquals(1, lease.manualResetCount.get())
        assertEquals(listOf("explode"), listener.startedInputs)
        assertEquals(listOf<Throwable>(thrown), listener.failures)
        assertEquals(1, lease.closeCount.get())
    }

    @Test
    fun `process closes lease when requestStarted throws`() {
        val lease = FakeWorkerLease { payload -> payload }
        val scheduler = InlineScheduler()
        val failures = mutableListOf<Throwable>()
        val listener =
            object : ServiceProcessorListener {
                override fun requestStarted(
                    scope: LeaseScope,
                    input: String,
                ): Unit = throw IllegalStateException("listener boom")

                override fun requestFailed(
                    scope: LeaseScope,
                    error: Throwable,
                ) {
                    failures += error
                }
            }
        val processor = ServiceProcessor({ lease }, scheduler, LineDelimitedResponseDecoder(), listener)

        val thrown =
            kotlin.test.assertFailsWith<IllegalStateException> {
                processor.process("payload")
            }

        assertEquals("listener boom", thrown.message)
        assertEquals(1, lease.manualResetCount.get())
        assertEquals(1, lease.closeCount.get())
        assertEquals(listOf<Throwable>(thrown), failures)
    }

    @Test
    fun `process propagates listener failure after reset`() {
        val lease = FakeWorkerLease { throw IllegalStateException("unreachable") }
        val scheduler = InlineScheduler()
        val listener =
            object : ServiceProcessorListener {
                override fun requestStarted(
                    scope: LeaseScope,
                    input: String,
                ) {}

                override fun requestFailed(
                    scope: LeaseScope,
                    error: Throwable,
                ): Unit = throw UnsupportedOperationException("listener bomb")
            }
        val processor = ServiceProcessor({ lease }, scheduler, LineDelimitedResponseDecoder(), listener)

        val thrown =
            kotlin.test.assertFailsWith<UnsupportedOperationException> {
                processor.process("explode")
            }

        assertEquals("listener bomb", thrown.message)
        assertEquals(2, lease.manualResetCount.get())
        assertEquals(1, lease.closeCount.get())
    }

    private class FakeWorkerLease(
        responder: (String) -> String,
    ) : WorkerLease {
        private val session = FakeInteractiveSession(responder)
        private val scope = TestLeaseScope()

        val closeCount = AtomicInteger()
        val manualResetCount = AtomicInteger()

        override fun session(): InteractiveSession = session

        override fun executionOptions(): ExecutionOptions = ExecutionOptions.builder().build()

        override fun scope(): LeaseScope = scope

        override fun reset(request: ResetRequest) {
            if (request.reason() == ResetRequest.Reason.MANUAL) {
                manualResetCount.incrementAndGet()
            }
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class TestLeaseScope(
        private val requestId: UUID = UUID.randomUUID(),
        private val workerId: Int = 1,
        private val createdAt: Instant = Instant.now(),
    ) : LeaseScope {
        override fun requestId(): UUID = requestId

        override fun workerId(): Int = workerId

        override fun leaseStart(): Instant = createdAt

        override fun workerCreatedAt(): Instant = createdAt

        override fun reuseCount(): Long = 0
    }
}
