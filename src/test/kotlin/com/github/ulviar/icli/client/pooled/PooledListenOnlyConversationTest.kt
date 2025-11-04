package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.ClientScheduler
import com.github.ulviar.icli.client.ClientSchedulers
import com.github.ulviar.icli.client.CommandResult
import com.github.ulviar.icli.client.ListenOnlySessionClient
import com.github.ulviar.icli.client.ResponseDecoder
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.WorkerLease
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest.Reason
import com.github.ulviar.icli.testing.FlowRecordingSubscriber
import com.github.ulviar.icli.testing.StreamingTestSession
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PooledListenOnlyConversationTest {
    private val fixtures = mutableListOf<ConversationFixture>()

    @AfterTest
    fun tearDown() {
        fixtures.forEach { it.scheduler.close() }
        fixtures.clear()
    }

    @Test
    fun `close stops streaming and returns worker to pool`() {
        val fixture = newFixture()
        val subscriber = FlowRecordingSubscriber()
        fixture.listenOnly.stdoutPublisher().subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        fixture.session.emitStdout("hello\n")
        subscriber.awaitCondition { subscriber.content().contains("hello") }

        fixture.conversation.close()

        val replacement = FlowRecordingSubscriber()
        fixture.listenOnly.stdoutPublisher().subscribe(replacement)
        replacement.awaitError()
        assertTrue(replacement.error is IllegalStateException)

        assertTrue(fixture.lease.closed)
        assertFalse(fixture.session.closed, "close() should not retire the worker")
    }

    @Test
    fun `retire stops streaming and closes worker`() {
        val fixture = newFixture()
        val subscriber = FlowRecordingSubscriber()
        fixture.listenOnly.stderrPublisher().subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        fixture.session.emitStderr("warn\n")
        subscriber.awaitCondition { subscriber.content().contains("warn") }

        fixture.conversation.retire()

        val replacement = FlowRecordingSubscriber()
        fixture.listenOnly.stdoutPublisher().subscribe(replacement)
        replacement.awaitError()
        assertTrue(replacement.error is IllegalStateException)

        assertTrue(fixture.lease.closed)
        assertTrue(fixture.session.closed, "retire() should close the interactive session")
        val reasons = fixture.lease.resetReasons
        assertEquals(listOf(Reason.CLIENT_RETIRE), reasons)
    }

    @Test
    fun `reset delegates to underlying conversation`() {
        val fixture = newFixture()

        fixture.conversation.reset()

        assertEquals(listOf(Reason.MANUAL), fixture.lease.resetReasons)
        assertFalse(fixture.session.closed)
        fixture.conversation.close()
    }

    private fun newFixture(): ConversationFixture {
        val scheduler = ClientSchedulers.virtualThreads()
        val session = StreamingTestSession()
        val lease = TestLease(session)
        val listener = NoopListener()
        val serviceConversation =
            ServiceConversation(lease, ResponseDecoder.lineDelimited(), scheduler, listener)
        val listenOnly = ListenOnlySessionClient.share(serviceConversation.interactive())
        val pooled = PooledListenOnlyConversation(serviceConversation, listenOnly)
        return ConversationFixture(pooled, listenOnly, lease, session, scheduler).also { fixtures += it }
    }

    private data class ConversationFixture(
        val conversation: PooledListenOnlyConversation,
        val listenOnly: ListenOnlySessionClient,
        val lease: TestLease,
        val session: StreamingTestSession,
        val scheduler: ClientScheduler,
    )

    private class TestLease(
        private val session: StreamingTestSession,
    ) : WorkerLease {
        private val scope = TestLeaseScope()
        val resetReasons = mutableListOf<Reason>()
        var closed: Boolean = false
            private set

        override fun session(): InteractiveSession = session

        override fun executionOptions(): ExecutionOptions = ExecutionOptions.builder().build()

        override fun scope(): LeaseScope = scope

        override fun reset(request: ResetRequest) {
            resetReasons += request.reason()
        }

        override fun close() {
            closed = true
        }
    }

    private class TestLeaseScope : LeaseScope {
        private val id = UUID.randomUUID()

        override fun requestId(): UUID = id

        override fun workerId(): Int = 1

        override fun leaseStart(): Instant = Instant.EPOCH

        override fun workerCreatedAt(): Instant = Instant.EPOCH

        override fun reuseCount(): Long = 0
    }

    private class NoopListener : ServiceProcessorListener {
        override fun requestStarted(
            scope: LeaseScope,
            input: String,
        ) {}

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
