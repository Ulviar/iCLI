package com.github.ulviar.icli.client

import com.github.ulviar.icli.testing.FlowRecordingSubscriber
import com.github.ulviar.icli.testing.StreamingTestSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListenOnlySessionClientTest {
    @Test
    fun `stdout publisher streams data`() {
        val session = StreamingTestSession()
        val client = ListenOnlySessionClient.wrap(InteractiveSessionClient.wrap(session))
        val subscriber = FlowRecordingSubscriber()

        client.stdoutPublisher().subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        session.emitStdout("alpha\n")
        session.emitStdout("beta\n")
        session.closeStdout()

        subscriber.awaitCompletion()
        assertEquals("alpha\nbeta\n", subscriber.content())
        assertTrue(subscriber.completed)
        assertNull(subscriber.error)
    }

    @Test
    fun `stopStreaming halts emissions without closing session`() {
        val session = StreamingTestSession()
        val client = ListenOnlySessionClient.wrap(InteractiveSessionClient.wrap(session))
        val subscriber = FlowRecordingSubscriber()

        client.stdoutPublisher().subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        session.emitStdout("one\n")
        subscriber.awaitCondition { subscriber.content().contains("one\n") }

        client.stopStreaming()
        val extraLatch = CountDownLatch(1)
        val watcher =
            object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
                override fun onSubscribe(subscription: java.util.concurrent.Flow.Subscription) {
                    subscription.cancel()
                    extraLatch.countDown()
                }

                override fun onNext(item: java.nio.ByteBuffer) {}

                override fun onError(throwable: Throwable) {}

                override fun onComplete() {}
            }
        client.stdoutPublisher().subscribe(watcher)
        extraLatch.await(100, TimeUnit.MILLISECONDS)
        session.emitStdout("two\n")
        extraLatch.await(100, TimeUnit.MILLISECONDS)

        assertEquals("one\n", subscriber.content())
        assertFalse(session.closed)
    }

    @Test
    fun `shared client closes streams without terminating session`() {
        val session = StreamingTestSession()
        val shared = ListenOnlySessionClient.share(InteractiveSessionClient.wrap(session))

        shared.close()

        assertFalse(session.closed)
    }

    @Test
    fun `owning client closes session`() {
        val session = StreamingTestSession()
        val client = ListenOnlySessionClient.wrap(InteractiveSessionClient.wrap(session))

        client.close()

        assertTrue(session.closed)
    }

    @Test
    fun `stderr publisher streams data`() {
        val session = StreamingTestSession()
        val client = ListenOnlySessionClient.wrap(InteractiveSessionClient.wrap(session))
        val subscriber = FlowRecordingSubscriber()

        client.stderrPublisher().subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        session.emitStderr("err-one\n")
        session.emitStderr("err-two\n")
        session.closeStderr()

        subscriber.awaitCompletion()
        assertEquals("err-one\nerr-two\n", subscriber.content())
    }

    @Test
    fun `publisher rejects second subscriber`() {
        val session = StreamingTestSession()
        val client = ListenOnlySessionClient.wrap(InteractiveSessionClient.wrap(session))
        val first = FlowRecordingSubscriber()
        client.stdoutPublisher().subscribe(first)
        first.request(Long.MAX_VALUE)

        val second = FlowRecordingSubscriber()
        client.stdoutPublisher().subscribe(second)

        second.awaitCondition { second.error != null }
        assertTrue(second.error is IllegalStateException)
    }
}
