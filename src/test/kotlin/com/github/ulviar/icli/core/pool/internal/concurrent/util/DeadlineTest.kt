package com.github.ulviar.icli.core.pool.internal.concurrent.util

import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadlineTest {
    @Test
    fun zeroAbsoluteCreatesInfiniteDeadline() {
        val deadline = Deadline.fromAbsoluteNanos(0)

        assertTrue(deadline.isInfinite())
        assertEquals(Deadline.INFINITE_NANOS, deadline.remainingNanos())
    }

    @Test
    fun zeroTimeoutProducesInfiniteDeadline() {
        val deadline = Deadline.fromTimeout(Duration.ZERO)

        assertTrue(deadline.isInfinite())
    }

    @Test
    fun negativeTimeoutRejected() {
        assertFailsWith<IllegalArgumentException> {
            Deadline.fromTimeout(Duration.ofMillis(-1))
        }
    }

    @Test
    fun veryLargeTimeoutSaturatesToInfinite() {
        val deadline = Deadline.fromTimeout(Duration.ofSeconds(Long.MAX_VALUE, 999_999_999))

        assertTrue(deadline.isInfinite())
        assertEquals(Deadline.INFINITE_NANOS, Deadline.toAbsoluteTimeout(Duration.ofSeconds(Long.MAX_VALUE)))
    }

    @Test
    fun remainingNanosPositiveBeforeDeadline() {
        val delta = Duration.ofMillis(250).toNanos()
        val deadline = Deadline.fromAbsoluteNanos(System.nanoTime() + delta)

        assertFalse(deadline.isExpired())
        assertTrue(deadline.remainingNanos() > 0)
    }

    @Test
    fun expiredDeadlineReportsZeroRemaining() {
        val deadline = Deadline.fromAbsoluteNanos(System.nanoTime())

        Thread.sleep(5)

        assertTrue(deadline.isExpired())
        assertEquals(0, deadline.remainingNanos())
    }

    @Test
    fun absoluteTimeoutRespectsDuration() {
        val timeout = Duration.ofMillis(10)
        val now = System.nanoTime()

        val absolute = Deadline.toAbsoluteTimeout(timeout, now)

        val elapsed = absolute - now
        assertTrue(elapsed >= 0)
        val tolerance = timeout.toNanos() + TimeUnit.MILLISECONDS.toNanos(5)
        assertTrue(elapsed <= tolerance)
    }
}
