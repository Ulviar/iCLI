package com.github.ulviar.icli.engine.pool.internal.concurrent

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BooleanSupplier
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LifecycleGateTest {
    private val lock = ReentrantLock(true)

    @Test
    fun markClosingIsIdempotent() {
        val gate = LifecycleGate(lock)
        lock.lock()
        try {
            assertTrue(gate.markClosing())
            assertTrue(gate.isClosing())
            assertFalse(gate.markClosing())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun markTerminatedIsIdempotent() {
        val gate = LifecycleGate(lock)
        lock.lock()
        try {
            assertFalse(gate.isTerminated())
            gate.markTerminated()
            assertTrue(gate.isTerminated())
            gate.markTerminated()
            assertTrue(gate.isTerminated())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun awaitDrainCompletesWhenActiveClears() {
        val gate = LifecycleGate(lock)
        val active = AtomicBoolean(true)
        val outcome = AtomicReference<LifecycleGate.DrainOutcome>()

        val waiter =
            thread {
                lock.lock()
                try {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                    outcome.set(gate.awaitDrain(deadline, BooleanSupplier { active.get() }))
                } finally {
                    lock.unlock()
                }
            }

        Thread.sleep(50)
        lock.lock()
        try {
            active.set(false)
            gate.signalStateChange()
        } finally {
            lock.unlock()
        }
        waiter.join()

        when (val result = outcome.get()) {
            is LifecycleGate.DrainOutcome.Completed -> {
                assertTrue(result.terminatedNow())
            }
            is LifecycleGate.DrainOutcome.TimedOut -> fail("expected drain completion")
        }
    }

    @Test
    fun awaitDrainTimesOutWhenDeadlineElapses() {
        val gate = LifecycleGate(lock)
        val active = AtomicBoolean(true)
        val outcome = AtomicReference<LifecycleGate.DrainOutcome>()

        val waiter =
            thread {
                lock.lock()
                try {
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)
                    outcome.set(gate.awaitDrain(deadline, BooleanSupplier { active.get() }))
                } finally {
                    lock.unlock()
                }
            }

        waiter.join()

        when (outcome.get()) {
            is LifecycleGate.DrainOutcome.Completed -> fail("expected drain timeout")
            is LifecycleGate.DrainOutcome.TimedOut -> Unit
        }
    }

    @Test
    fun awaitDrainHonoursZeroDeadlineAsIndefiniteWait() {
        val gate = LifecycleGate(lock)
        val active = AtomicBoolean(true)
        val outcome = AtomicReference<LifecycleGate.DrainOutcome>()

        val waiter =
            thread {
                lock.lock()
                try {
                    outcome.set(gate.awaitDrain(0, BooleanSupplier { active.get() }))
                } finally {
                    lock.unlock()
                }
            }

        Thread.sleep(50)
        lock.lock()
        try {
            active.set(false)
            gate.signalStateChange()
        } finally {
            lock.unlock()
        }
        waiter.join()

        when (val result = outcome.get()) {
            is LifecycleGate.DrainOutcome.Completed -> assertTrue(result.terminatedNow())
            is LifecycleGate.DrainOutcome.TimedOut -> fail("expected drain completion")
        }
    }

    @Test
    fun awaitDrainReportsAlreadyTerminated() {
        val gate = LifecycleGate(lock)
        lock.lock()
        try {
            gate.markTerminated()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            val outcome = gate.awaitDrain(deadline, BooleanSupplier { false })
            when (outcome) {
                is LifecycleGate.DrainOutcome.Completed -> assertFalse(outcome.terminatedNow())
                is LifecycleGate.DrainOutcome.TimedOut -> fail("expected completed drain")
            }
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun multipleAwaitDrainCallersCoordinateTerminationFlag() {
        val gate = LifecycleGate(lock)
        val active = AtomicInteger(3)
        val ready = CountDownLatch(3)
        val outcomes = CopyOnWriteArrayList<LifecycleGate.DrainOutcome>()

        val threads =
            List(3) {
                Thread.ofVirtual().start {
                    lock.lock()
                    try {
                        ready.countDown()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                        outcomes += gate.awaitDrain(deadline, BooleanSupplier { active.get() > 0 })
                    } finally {
                        lock.unlock()
                    }
                }
            }

        ready.await()
        Thread.sleep(20)
        lock.lock()
        try {
            active.set(0)
            gate.signalStateChange()
        } finally {
            lock.unlock()
        }

        threads.forEach { it.join() }

        val completed = outcomes.filterIsInstance<LifecycleGate.DrainOutcome.Completed>()
        assertEquals(3, completed.size)
        assertEquals(1, completed.count { it.terminatedNow() })
    }

    @Test
    fun spuriousSignalsDoNotCompleteWhileActiveWorkersRemain() {
        val gate = LifecycleGate(lock)
        val active = AtomicInteger(1)
        val ready = CountDownLatch(1)
        val outcome = AtomicReference<LifecycleGate.DrainOutcome>()

        val waiter =
            Thread.ofVirtual().start {
                lock.lock()
                try {
                    ready.countDown()
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(150)
                    outcome.set(gate.awaitDrain(deadline, BooleanSupplier { active.get() > 0 }))
                } finally {
                    lock.unlock()
                }
            }

        ready.await()
        lock.lock()
        try {
            gate.signalStateChange()
        } finally {
            lock.unlock()
        }

        waiter.join()

        when (outcome.get()) {
            is LifecycleGate.DrainOutcome.Completed -> fail("expected timeout due to active workers")
            is LifecycleGate.DrainOutcome.TimedOut -> Unit
        }
    }

    @Test
    fun awaitDrainPropagatesInterruptAndCallerRestoresFlag() {
        val gate = LifecycleGate(lock)
        val active = AtomicBoolean(true)
        val ready = CountDownLatch(1)
        val caught = AtomicReference<InterruptedException?>()
        val flagRestored = AtomicBoolean(false)

        val waiter =
            Thread.ofVirtual().start {
                lock.lock()
                try {
                    ready.countDown()
                    try {
                        gate.awaitDrain(0, BooleanSupplier { active.get() })
                        fail("expected interruption")
                    } catch (ex: InterruptedException) {
                        Thread.currentThread().interrupt()
                        flagRestored.set(Thread.currentThread().isInterrupted)
                        caught.set(ex)
                    }
                } finally {
                    lock.unlock()
                }
            }

        ready.await()
        Thread.sleep(20)
        waiter.interrupt()
        waiter.join()

        assertNotNull(caught.get())
        assertTrue(flagRestored.get())
    }
}
