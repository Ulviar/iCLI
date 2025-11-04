package com.github.ulviar.icli.testing

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Test helper that records {@link ByteBuffer} payloads emitted by a single-subscriber publisher.
 */
class FlowRecordingSubscriber(
    private val charset: Charset = StandardCharsets.UTF_8,
) : Flow.Subscriber<ByteBuffer> {
    private lateinit var subscription: Flow.Subscription
    private val values = StringBuilder()

    @Volatile var completed: Boolean = false
        private set

    @Volatile var error: Throwable? = null
        private set

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
    }

    override fun onNext(item: ByteBuffer) {
        val bytes = ByteArray(item.remaining())
        item.get(bytes)
        values.append(String(bytes, charset))
    }

    override fun onError(throwable: Throwable) {
        error = throwable
    }

    override fun onComplete() {
        completed = true
    }

    fun request(n: Long) {
        subscription.request(n)
    }

    fun content(): String = values.toString()

    fun awaitCompletion(timeout: Duration = 2.seconds) {
        awaitCondition(timeout) { completed }
    }

    fun awaitError(timeout: Duration = 2.seconds) {
        awaitCondition(timeout) { error != null }
    }

    fun awaitCondition(
        timeout: Duration = 2.seconds,
        predicate: () -> Boolean,
    ) {
        val deadline = TimeSource.Monotonic.markNow()
        while (deadline.elapsedNow() < timeout) {
            if (predicate()) {
                return
            }
            Thread.sleep(PAUSE_MILLIS)
        }
        error("Timed out after $timeout waiting for condition; observed $values")
    }

    private companion object {
        private const val PAUSE_MILLIS: Long = 10
    }
}
