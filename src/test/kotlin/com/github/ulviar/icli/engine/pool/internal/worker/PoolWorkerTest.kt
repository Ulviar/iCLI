package com.github.ulviar.icli.engine.pool.internal.worker

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PoolWorkerTest {
    @Test
    fun `markReturned updates reuse data only once`() {
        val createdAt = Instant.parse("2025-10-30T00:00:00Z")
        val worker = newWorker(createdAt)
        val leaseId = UUID.randomUUID()
        val firstReturn = Instant.parse("2025-10-30T01:00:00Z")
        val secondReturn = Instant.parse("2025-10-30T02:00:00Z")

        assertFalse(worker.markReturned(firstReturn))
        assertEquals(createdAt, worker.lastUsed())
        assertEquals(0, worker.reuseCount())

        worker.markLeased(leaseId)
        val firstResult = worker.markReturned(firstReturn)

        assertTrue(firstResult)
        assertEquals(1, worker.reuseCount())
        assertEquals(firstReturn, worker.lastUsed())
        assertNull(worker.activeRequestId())

        val secondResult = worker.markReturned(secondReturn)

        assertFalse(secondResult)
        assertEquals(1, worker.reuseCount(), "reuse count should not increment after redundant return")
        assertEquals(firstReturn, worker.lastUsed(), "lastUsed should remain unchanged after redundant return")
    }

    @Test
    fun `active request id is set on lease and cleared on return`() {
        val worker = newWorker()
        val leaseId = UUID.randomUUID()

        worker.markLeased(leaseId)
        assertEquals(leaseId, worker.activeRequestId())

        worker.markReturned(Instant.parse("2025-10-30T03:00:00Z"))
        assertNull(worker.activeRequestId())
    }

    @Test
    fun `requestRetire keeps first reason`() {
        val worker = newWorker()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val first = WorkerRetirementReason.RETIRE_REQUESTED
        val second = WorkerRetirementReason.REQUEST_TIMEOUT
        val executor = Executors.newFixedThreadPool(2)

        try {
            fun submit(reason: WorkerRetirementReason) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    worker.requestRetire(reason)
                }
            }

            submit(first)
            submit(second)
            assertTrue(ready.await(1, TimeUnit.SECONDS))
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))

            val recorded = worker.retirementCause()
            assertTrue(
                recorded == first || recorded == second,
                "retirement cause should be one of the submitted reasons",
            )

            worker.requestRetire(if (recorded == first) second else first)
            assertEquals(
                recorded,
                worker.retirementCause(),
                "retirement cause should remain unchanged after first write",
            )
            assertTrue(worker.retireRequested())
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `markLeased rejects duplicate leasing`() {
        val worker = newWorker()
        val leaseId = UUID.randomUUID()

        worker.markLeased(leaseId)

        val error =
            runCatching { worker.markLeased(UUID.randomUUID()) }
                .exceptionOrNull()
                ?: fail("Expected IllegalStateException when leasing an already leased worker")

        assertIs<IllegalStateException>(error)
    }

    private fun newWorker(createdAt: Instant = Instant.parse("2025-10-30T00:00:00Z")): PoolWorker {
        val session =
            object : InteractiveSession {
                override fun stdin(): OutputStream = throw UnsupportedOperationException("not used")

                override fun stdout(): InputStream = throw UnsupportedOperationException("not used")

                override fun stderr(): InputStream = throw UnsupportedOperationException("not used")

                override fun onExit(): CompletableFuture<Int> = CompletableFuture.completedFuture(0)

                override fun closeStdin() {}

                override fun sendSignal(signal: ShutdownSignal) {}

                override fun resizePty(
                    columns: Int,
                    rows: Int,
                ) {}

                override fun close() {}
            }
        val options = ExecutionOptions.builder().build()
        return PoolWorker(42, session, options, createdAt)
    }
}
