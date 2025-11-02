package com.github.ulviar.icli.engine.pool.internal.concurrent

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.SplittableRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class WaiterQueueTest {
    private val lock = ReentrantLock(true)

    @Test
    fun assignsWorkersInFifoOrder() {
        val queue = WaiterQueue(lock)
        val firstAssignment = AtomicReference<PoolWorker>()
        val secondAssignment = AtomicReference<PoolWorker>()

        val firstWaiter: WaiterQueue.Waiter
        val secondWaiter: WaiterQueue.Waiter
        lock.lock()
        try {
            firstWaiter = queue.enqueue()
            secondWaiter = queue.enqueue()
        } finally {
            lock.unlock()
        }

        val ready = CountDownLatch(1)
        val assigner =
            thread {
                ready.await()
                lock.lock()
                try {
                    queue.assignToNext(newWorker(1))
                    queue.assignToNext(newWorker(2))
                } finally {
                    lock.unlock()
                }
            }

        lock.lock()
        try {
            ready.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            val firstOutcome = firstWaiter.awaitAssignment(deadline)
            firstAssignment.set(
                when (firstOutcome) {
                    is WaiterQueue.AwaitOutcome.Assigned -> firstOutcome.worker()
                    is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected worker assignment")
                    is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected worker assignment")
                },
            )

            val secondOutcome = secondWaiter.awaitAssignment(deadline)
            secondAssignment.set(
                when (secondOutcome) {
                    is WaiterQueue.AwaitOutcome.Assigned -> secondOutcome.worker()
                    is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected worker assignment")
                    is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected worker assignment")
                },
            )
        } finally {
            lock.unlock()
        }

        assigner.join()

        assertEquals(1, firstAssignment.get().id())
        assertEquals(2, secondAssignment.get().id())
        assertTrue(firstWaiter.isAssigned())
        assertTrue(secondWaiter.isAssigned())
    }

    @Test
    fun assignToNextReturnsFalseWhenQueueEmpty() {
        val queue = WaiterQueue(lock)
        lock.lock()
        try {
            assertFalse(queue.assignToNext(newWorker(99)))
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun timeoutRemovesWaiter() {
        val queue = WaiterQueue(lock)
        val waiter: WaiterQueue.Waiter
        lock.lock()
        try {
            waiter = queue.enqueue()
            val result = waiter.awaitAssignment(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50))
            assertTrue(result is WaiterQueue.AwaitOutcome.TimedOut)
            assertEquals(0, queue.size())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun preCancelledWaiterReturnsCancelled() {
        val queue = WaiterQueue(lock)
        val waiter: WaiterQueue.Waiter
        lock.lock()
        try {
            waiter = queue.enqueue()
            queue.cancel(waiter)
        } finally {
            lock.unlock()
        }

        lock.lock()
        try {
            val outcome = waiter.awaitAssignment(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10))
            when (outcome) {
                is WaiterQueue.AwaitOutcome.Assigned -> fail("expected cancellation outcome")
                is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected cancellation outcome")
                is WaiterQueue.AwaitOutcome.Cancelled -> Unit
            }
            assertTrue(queue.isEmpty())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun deadlineExpiringImmediatelyTimesOut() {
        val queue = WaiterQueue(lock)
        val waiter: WaiterQueue.Waiter
        lock.lock()
        try {
            waiter = queue.enqueue()
        } finally {
            lock.unlock()
        }

        lock.lock()
        try {
            val outcome = waiter.awaitAssignment(System.nanoTime())
            when (outcome) {
                is WaiterQueue.AwaitOutcome.Assigned -> fail("expected timeout outcome")
                is WaiterQueue.AwaitOutcome.TimedOut -> Unit
                is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected timeout outcome")
            }
            assertEquals(0, queue.size())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun operationsRequireHoldingLock() {
        val queue = WaiterQueue(lock)

        assertFailsWith<IllegalStateException> { queue.enqueue() }
        assertFailsWith<IllegalStateException> { queue.assignToNext(newWorker(1)) }
        assertFailsWith<IllegalStateException> { queue.size() }

        lock.lock()
        try {
            queue.enqueue()
        } finally {
            lock.unlock()
        }

        assertFailsWith<IllegalStateException> { queue.cancelAll() }

        lock.lock()
        try {
            queue.cancelAll()
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun assignmentBeforeAwaitReturnsImmediately() {
        val queue = WaiterQueue(lock)
        lock.lock()
        try {
            val waiter = queue.enqueue()
            queue.assignToNext(newWorker(7))

            val outcome = waiter.awaitAssignment(System.nanoTime() + TimeUnit.SECONDS.toNanos(1))
            when (outcome) {
                is WaiterQueue.AwaitOutcome.Assigned -> assertEquals(7, outcome.worker().id())
                is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected assignment before timeout")
                is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected assignment before cancellation")
            }
            assertTrue(waiter.isAssigned())
            assertTrue(queue.isEmpty())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun cancelledWaiterDoesNotReceiveWorker() {
        val queue = WaiterQueue(lock)
        val first: WaiterQueue.Waiter
        val second: WaiterQueue.Waiter
        lock.lock()
        try {
            first = queue.enqueue()
            second = queue.enqueue()
            queue.cancel(first)
        } finally {
            lock.unlock()
        }

        val outcome = AtomicReference<WaiterQueue.AwaitOutcome>()
        val waiterThread =
            thread {
                lock.lock()
                try {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                    outcome.set(second.awaitAssignment(deadline))
                } finally {
                    lock.unlock()
                }
            }

        Thread.sleep(50)

        lock.lock()
        try {
            assertEquals(1, queue.size())
            queue.assignToNext(newWorker(42))
        } finally {
            lock.unlock()
        }

        waiterThread.join()
        val result = outcome.get()
        when (result) {
            is WaiterQueue.AwaitOutcome.Assigned -> assertEquals(42, result.worker().id())
            is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected assignment outcome")
            is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected assignment outcome")
        }

        lock.lock()
        try {
            assertTrue(queue.isEmpty())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun cancelAllCancelsAndWakesWaiters() {
        val queue = WaiterQueue(lock)
        val waiter: WaiterQueue.Waiter
        lock.lock()
        try {
            waiter = queue.enqueue()
        } finally {
            lock.unlock()
        }

        val outcome = AtomicReference<WaiterQueue.AwaitOutcome>()
        val waiting = CountDownLatch(1)
        val waiterThread =
            thread {
                lock.lock()
                try {
                    waiting.countDown()
                    outcome.set(waiter.awaitAssignment(0))
                } finally {
                    lock.unlock()
                }
            }

        waiting.await()
        Thread.sleep(50)

        lock.lock()
        try {
            queue.cancelAll()
            queue.cancelAll()
        } finally {
            lock.unlock()
        }

        waiterThread.join()
        when (val result = outcome.get()) {
            is WaiterQueue.AwaitOutcome.Assigned -> fail("expected cancellation outcome")
            is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected cancellation outcome")
            is WaiterQueue.AwaitOutcome.Cancelled -> Unit
        }

        lock.lock()
        try {
            assertTrue(queue.isEmpty())
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun multipleWaitersReceiveUniqueWorkers() {
        val queue = WaiterQueue(lock)
        val waiters = ArrayList<WaiterQueue.Waiter>()
        lock.lock()
        try {
            repeat(3) {
                waiters += queue.enqueue()
            }
        } finally {
            lock.unlock()
        }

        val results = ConcurrentLinkedQueue<Int>()
        val ready = CountDownLatch(3)
        val done = CountDownLatch(3)

        waiters.forEachIndexed { index, waiter ->
            thread(name = "waiter-$index") {
                lock.lock()
                try {
                    ready.countDown()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                    val outcome = waiter.awaitAssignment(deadline)
                    val workerId =
                        when (outcome) {
                            is WaiterQueue.AwaitOutcome.Assigned -> outcome.worker().id()
                            is WaiterQueue.AwaitOutcome.TimedOut -> fail("expected assignment")
                            is WaiterQueue.AwaitOutcome.Cancelled -> fail("expected assignment")
                        }
                    results.add(workerId)
                } finally {
                    lock.unlock()
                    done.countDown()
                }
            }
        }

        ready.await()

        val assigner =
            thread {
                lock.lock()
                try {
                    repeat(3) { index ->
                        queue.assignToNext(newWorker(index + 1))
                    }
                } finally {
                    lock.unlock()
                }
            }

        done.await()
        assigner.join()

        assertEquals(setOf(1, 2, 3), results.toSet())
    }

    @Test
    fun fuzzyInteractionsPreserveFifoAndState() {
        val random = SplittableRandom(87234)
        repeat(200) {
            runFuzzScenario(random)
        }
    }

    private fun newWorker(id: Int): PoolWorker =
        PoolWorker(
            id,
            TestInteractiveSession(),
            ExecutionOptions.builder().build(),
            Instant.EPOCH,
        )

    private fun runFuzzScenario(random: SplittableRandom) {
        val scenarioLock = ReentrantLock(true)
        val queue = WaiterQueue(scenarioLock)
        val waiterCount = random.nextInt(1, 6)

        val categories =
            MutableList(waiterCount) {
                Category.values()[random.nextInt(Category.values().size)]
            }.also { if (it.none { c -> c == Category.ASSIGN }) it[random.nextInt(waiterCount)] = Category.ASSIGN }

        val waiters = arrayOfNulls<WaiterQueue.Waiter>(waiterCount)
        val outcomes = Array(waiterCount) { AtomicReference<WaiterQueue.AwaitOutcome?>(null) }
        val ready = CountDownLatch(waiterCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(waiterCount)

        val threads =
            (0 until waiterCount).map { index ->
                thread(name = "waiter-fuzz-$index") {
                    // 1) enqueue — под замком
                    scenarioLock.lock()
                    try {
                        waiters[index] = queue.enqueue()
                    } finally {
                        scenarioLock.unlock()
                    }
                    ready.countDown()

                    start.await()

                    val deadline =
                        if (categories[index] == Category.TIMEOUT) {
                            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2)
                        } else {
                            0L
                        }

                    scenarioLock.lock()
                    try {
                        val outcome = requireNotNull(waiters[index]).awaitAssignment(deadline)
                        outcomes[index].set(outcome)
                    } finally {
                        scenarioLock.unlock()
                        done.countDown()
                    }
                }
            }

        ready.await()

        scenarioLock.lock()
        try {
            categories.forEachIndexed { i, cat ->
                if (cat == Category.CANCEL) queue.cancel(requireNotNull(waiters[i]))
            }
        } finally {
            scenarioLock.unlock()
        }

        start.countDown()

        if (categories.any { it == Category.TIMEOUT }) Thread.sleep(5)

        scenarioLock.lock()
        try {
            var workerId = 1
            repeat(categories.count { it == Category.ASSIGN }) {
                assertTrue(queue.assignToNext(newWorker(workerId++)))
            }
        } finally {
            scenarioLock.unlock()
        }

        done.await()
        threads.forEach(Thread::join)

        val assignedIndices = mutableListOf<Int>()
        val assignedWorkers = mutableListOf<Int>()

        categories.forEachIndexed { index, category ->
            val outcome = outcomes[index].get()
            when (category) {
                Category.CANCEL ->
                    assertTrue(
                        outcome is WaiterQueue.AwaitOutcome.Cancelled,
                        "expected cancellation for $index but was $outcome",
                    )
                Category.TIMEOUT ->
                    assertTrue(
                        outcome is WaiterQueue.AwaitOutcome.TimedOut,
                        "expected timeout for $index but was $outcome",
                    )
                Category.ASSIGN -> {
                    val assigned =
                        outcome as? WaiterQueue.AwaitOutcome.Assigned
                            ?: fail("expected assignment for $index but was $outcome")
                    assignedIndices += index
                    assignedWorkers += assigned.worker().id()
                }
            }
        }

        val expectedAssigned = categories.indices.filter { categories[it] == Category.ASSIGN }
        assertEquals(expectedAssigned, assignedIndices)
        assertEquals(expectedAssigned.size, assignedWorkers.distinct().size)

        scenarioLock.lock()
        try {
            assertEquals(0, queue.size())
        } finally {
            scenarioLock.unlock()
        }
    }

    private enum class Category {
        ASSIGN,
        CANCEL,
        TIMEOUT,
    }

    private class TestInteractiveSession : InteractiveSession {
        override fun stdin(): OutputStream = OutputStream.nullOutputStream()

        override fun stdout(): InputStream = InputStream.nullInputStream()

        override fun stderr(): InputStream = InputStream.nullInputStream()

        override fun onExit(): CompletableFuture<Int> = CompletableFuture.completedFuture(0)

        override fun closeStdin() {}

        override fun sendSignal(signal: ShutdownSignal) {}

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {}

        override fun close() {}
    }
}
