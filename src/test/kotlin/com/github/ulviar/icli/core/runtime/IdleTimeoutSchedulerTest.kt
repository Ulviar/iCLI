package com.github.ulviar.icli.core.runtime

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdleTimeoutSchedulerTest {
    @Test
    fun `no-op scheduler never executes callback`() {
        val counter = AtomicInteger()
        val scheduler = IdleTimeoutScheduler.create(Duration.ZERO) { counter.incrementAndGet() }

        scheduler.use {
            it.reschedule()
            it.reschedule()
            it.cancel()
            // give the scheduler a moment even though it should no-op
            TimeUnit.MILLISECONDS.sleep(50)
            assertEquals(0, counter.get())
        }
    }

    @Test
    fun `reschedule triggers callback after timeout`() {
        val latch = CountDownLatch(1)
        val scheduler = IdleTimeoutScheduler.create(Duration.ofMillis(25)) { latch.countDown() }

        scheduler.use {
            it.reschedule()
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "idle callback not invoked")
        }
    }

    @Test
    fun `subsequent reschedule replaces pending task`() {
        val counter = AtomicInteger()
        val scheduler = IdleTimeoutScheduler.create(Duration.ofMillis(100)) { counter.incrementAndGet() }

        scheduler.use {
            it.reschedule()
            TimeUnit.MILLISECONDS.sleep(30)
            it.reschedule()

            TimeUnit.MILLISECONDS.sleep(150)
            assertEquals(1, counter.get(), "expected only the latest scheduling to run")
        }
    }

    @Test
    fun `cancel prevents callback execution`() {
        val counter = AtomicInteger()
        val scheduler = IdleTimeoutScheduler.create(Duration.ofMillis(50)) { counter.incrementAndGet() }

        scheduler.use {
            it.reschedule()
            it.cancel()
            TimeUnit.MILLISECONDS.sleep(150)
            assertEquals(0, counter.get())
        }
    }

    @Test
    fun `close cancels pending task and blocks further scheduling`() {
        val counter = AtomicInteger()
        val latch = CountDownLatch(1)
        val scheduler =
            IdleTimeoutScheduler.create(Duration.ofMillis(40)) {
                counter.incrementAndGet()
                latch.countDown()
            }

        scheduler.reschedule()
        scheduler.close()

        assertFalse(latch.await(200, TimeUnit.MILLISECONDS), "callback should not run after close")

        scheduler.reschedule()
        TimeUnit.MILLISECONDS.sleep(100)
        assertEquals(0, counter.get())
    }
}
