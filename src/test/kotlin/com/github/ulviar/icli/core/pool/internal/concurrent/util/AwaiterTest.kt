package com.github.ulviar.icli.core.pool.internal.concurrent.util

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AwaiterTest {
    private val lock = ReentrantLock(true)
    private val condition = lock.newCondition()

    @Test
    fun completesWhenPredicateBecomesFalse() {
        val active = AtomicBoolean(true)
        val outcome = AtomicReference<Awaiter.Result>()
        val waitStarted = CountDownLatch(1)

        val waiter =
            thread {
                lock.lock()
                try {
                    waitStarted.countDown()
                    val deadline = Deadline.fromTimeout(Duration.ofSeconds(1))
                    outcome.set(Awaiter.await(lock, condition, deadline) { active.get() })
                } finally {
                    lock.unlock()
                }
            }

        assertTrue(waitStarted.await(1, TimeUnit.SECONDS))

        Thread.sleep(20)
        lock.lock()
        try {
            active.set(false)
            condition.signalAll()
        } finally {
            lock.unlock()
        }
        waiter.join()

        assertEquals(Awaiter.Result.COMPLETED, outcome.get())
    }

    @Test
    fun timesOutWhenDeadlineElapses() {
        lock.lock()
        try {
            val deadline = Deadline.fromTimeout(Duration.ofMillis(50))
            val result = Awaiter.await(lock, condition, deadline) { true }
            assertEquals(Awaiter.Result.TIMED_OUT, result)
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun spuriousSignalDoesNotCompletePredicate() {
        val active = AtomicBoolean(true)
        val outcome = AtomicReference<Awaiter.Result?>()
        val waitStarted = CountDownLatch(1)

        val waiter =
            thread {
                lock.lock()
                try {
                    waitStarted.countDown()
                    val deadline = Deadline.fromTimeout(Duration.ofSeconds(1))
                    outcome.set(Awaiter.await(lock, condition, deadline) { active.get() })
                } finally {
                    lock.unlock()
                }
            }

        assertTrue(waitStarted.await(1, TimeUnit.SECONDS))

        lock.lock()
        try {
            condition.signalAll()
        } finally {
            lock.unlock()
        }

        Thread.sleep(20)
        assertTrue(outcome.get() == null)

        lock.lock()
        try {
            active.set(false)
            condition.signalAll()
        } finally {
            lock.unlock()
        }

        waiter.join()
        assertEquals(Awaiter.Result.COMPLETED, outcome.get())
    }

    @Test
    fun interruptPropagatesOutOfAwait() {
        val interrupted = AtomicBoolean(false)
        val waitStarted = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        val waiter =
            thread {
                lock.lock()
                try {
                    waitStarted.countDown()
                    try {
                        Awaiter.await(lock, condition, Deadline.infinite()) { true }
                        fail("expected interrupt")
                    } catch (ex: InterruptedException) {
                        Thread.currentThread().interrupt()
                        interrupted.set(true)
                    }
                } catch (ex: Throwable) {
                    failure.set(ex)
                } finally {
                    lock.unlock()
                }
            }

        assertTrue(waitStarted.await(1, TimeUnit.SECONDS))
        waiter.interrupt()
        waiter.join()

        assertTrue(interrupted.get())
        assertEquals(null, failure.get(), "unexpected failure while awaiting")
        assertTrue(waiter.isInterrupted)
    }
}
