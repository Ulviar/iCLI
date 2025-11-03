package com.github.ulviar.icli.client

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.WorkerLease
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServiceConversationTest {
    @Test
    fun `reset delegates to lease and notifies listener`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.reset()

        assertEquals(listOf(ResetRequest.Reason.MANUAL), lease.resetReasons)
        assertEquals(1, listener.conversationResetCount)
        conversation.close()
    }

    @Test
    fun `line and interactive clients share the same session`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        val line = conversation.line()
        assertSame(conversation.interactive(), line.interactive())

        conversation.close()
    }

    @Test
    fun `scope exposes underlying lease metadata`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        assertSame(lease.exposedScope(), conversation.scope())

        conversation.close()
    }

    @Test
    fun `close returns lease without closing session`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.close()

        assertEquals(1, lease.closeCount.get())
        assertFalse(lease.sessionClosed())
        assertEquals(1, listener.conversationClosedCount)
        assertTrue(lease.resetReasons.isEmpty())
    }

    @Test
    fun `close is idempotent`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.close()
        conversation.close()

        assertEquals(1, lease.closeCount.get())
        assertEquals(1, listener.conversationClosingCount)
        assertEquals(1, listener.conversationClosedCount)
    }

    @Test
    fun `retire resets lease and closes session`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.retire()

        assertEquals(listOf(ResetRequest.Reason.CLIENT_RETIRE), lease.resetReasons)
        assertTrue(lease.sessionClosed())
        assertEquals(1, lease.closeCount.get())
        assertEquals(1, listener.conversationResetCount)
        assertEquals(1, listener.conversationClosedCount)
    }

    @Test
    fun `listener lifecycle callbacks fire in documented order`() {
        val lease = FakeWorkerLease()
        val events = mutableListOf<String>()
        val listener =
            object : ServiceProcessorListener {
                override fun conversationOpened(scope: LeaseScope) {
                    events += "opened"
                }

                override fun conversationReset(scope: LeaseScope) {
                    events += "reset"
                }

                override fun conversationClosing(scope: LeaseScope) {
                    events += "closing"
                }

                override fun conversationClosed(scope: LeaseScope) {
                    events += "closed"
                }
            }
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.reset()
        conversation.retire()

        assertEquals(listOf("opened", "reset", "closing", "reset", "closed"), events)
        assertEquals(
            listOf(ResetRequest.Reason.MANUAL, ResetRequest.Reason.CLIENT_RETIRE),
            lease.resetReasons,
        )
    }

    @Test
    fun `retire ignores repeated invocations`() {
        val lease = FakeWorkerLease()
        val listener = RecordingListener()
        val conversation = ServiceConversation(lease, LineDelimitedResponseDecoder(), InlineScheduler(), listener)

        conversation.retire()
        conversation.retire()
        conversation.close()

        assertEquals(listOf(ResetRequest.Reason.CLIENT_RETIRE), lease.resetReasons)
        assertEquals(1, lease.closeCount.get())
    }

    private class FakeWorkerLease : WorkerLease {
        private val scope = TestLeaseScope()
        private val session = FakeInteractiveSession { payload -> payload }

        val closeCount = AtomicInteger()
        val resetReasons = mutableListOf<ResetRequest.Reason>()

        fun exposedScope(): LeaseScope = scope

        override fun session(): com.github.ulviar.icli.engine.InteractiveSession = session

        override fun executionOptions(): ExecutionOptions = ExecutionOptions.builder().build()

        override fun scope(): LeaseScope = scope

        override fun reset(request: ResetRequest) {
            resetReasons += request.reason()
        }

        override fun close() {
            closeCount.incrementAndGet()
        }

        fun sessionClosed(): Boolean = session.isClosed()
    }

    private class TestLeaseScope(
        private val id: UUID = UUID.randomUUID(),
        private val workerId: Int = 1,
        private val createdAt: Instant = Instant.now(),
    ) : LeaseScope {
        override fun requestId(): UUID = id

        override fun workerId(): Int = workerId

        override fun leaseStart(): Instant = createdAt

        override fun workerCreatedAt(): Instant = createdAt

        override fun reuseCount(): Long = 0
    }
}
