package com.github.ulviar.icli.core.pool.internal.lease

import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DefaultRequestTimeoutSchedulerTest {
    @Test
    fun cancelPreventsTimeoutExecution() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val fired = AtomicBoolean(false)
        try {
            scheduler.schedule(1, UUID.randomUUID(), Duration.ofMillis(50)) { fired.set(true) }
            scheduler.cancel(1)

            TimeUnit.MILLISECONDS.sleep(100)
            assertFalse(fired.get(), "Timeout callback should not fire after cancel")
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun completeCancelsMatchingTimeout() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val fired = AtomicBoolean(false)
        val requestId = UUID.randomUUID()
        try {
            scheduler.schedule(2, requestId, Duration.ofMillis(100)) { fired.set(true) }
            val cancelled = scheduler.complete(2, requestId)

            TimeUnit.MILLISECONDS.sleep(75)
            assertTrue(cancelled, "Expected matching request to cancel the timeout")
            assertFalse(fired.get(), "Timeout callback should not fire after successful complete")
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun mismatchedRequestDoesNotCancelTimeout() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val latch = CountDownLatch(1)
        try {
            scheduler.schedule(3, UUID.randomUUID(), Duration.ofMillis(50)) { latch.countDown() }
            val cancelled = scheduler.complete(3, UUID.randomUUID())
            assertFalse(cancelled, "Different requestId must not cancel the timeout")

            assertTrue(latch.await(200, TimeUnit.MILLISECONDS), "Timeout should eventually fire")
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun scheduleWithHugeDurationDoesNotOverflow() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val workerId = 41
        try {
            val result =
                runCatching {
                    scheduler.schedule(
                        workerId,
                        UUID.randomUUID(),
                        Duration.ofSeconds(Long.MAX_VALUE, 999_999_999),
                    ) {}
                }
            result.exceptionOrNull()?.let { fail("Scheduling with a huge duration must not overflow: ${it.message}") }
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun zeroDurationStillSchedulesWithPositiveDelay() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val latch = CountDownLatch(1)
        try {
            scheduler.schedule(11, UUID.randomUUID(), Duration.ZERO) { latch.countDown() }

            assertFalse(
                latch.await(0, TimeUnit.MILLISECONDS),
                "Timeout callback should not fire synchronously",
            )
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS), "Timeout callback should eventually fire")
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun closeCancelsOutstandingTimeouts() {
        val scheduler = DefaultRequestTimeoutScheduler()
        val fired = AtomicBoolean(false)
        scheduler.schedule(19, UUID.randomUUID(), Duration.ofSeconds(5)) { fired.set(true) }

        scheduler.close()
        TimeUnit.MILLISECONDS.sleep(100)

        assertFalse(fired.get(), "Timeout callback must not fire after scheduler close")
    }
}
