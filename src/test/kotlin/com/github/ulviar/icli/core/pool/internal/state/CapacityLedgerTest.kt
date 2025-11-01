package com.github.ulviar.icli.core.pool.internal.state

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.InteractiveSession
import com.github.ulviar.icli.core.ShutdownSignal
import com.github.ulviar.icli.core.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.core.pool.api.WorkerRetirementReason
import com.github.ulviar.icli.core.pool.internal.worker.PoolWorker
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CapacityLedgerTest {
    @Test
    fun reserveLaunchRespectsMaxSize() {
        val ledger = ledger(maxSize = 2)
        val first = ledger.reserveLaunchWorkerId()
        val second = ledger.reserveLaunchWorkerId()
        val third = ledger.reserveLaunchWorkerId()

        assertTrue(first >= 0)
        assertTrue(second >= 0)
        assertEquals(-1, third)

        ledger.discardLaunchReservation(false)
        ledger.discardLaunchReservation(false)
    }

    @Test
    fun beginLeaseTracksCounts() {
        val ledger = ledger(maxSize = 2)
        val workerId = ledger.reserveLaunchWorkerId()
        require(workerId >= 0)
        val worker = newWorker(workerId)
        ledger.registerLaunch()
        ledger.enqueueReturnedIdle(worker)

        val retired = mutableListOf<RetiredWorker>()
        val polled = ledger.pollIdle(retired, Instant.EPOCH)
        val scope = ledger.beginLease(polled.orElseThrow(), Instant.EPOCH)
        assertEquals(workerId, scope.workerId())

        val returned = ledger.returnLease(worker, Instant.EPOCH)
        assertTrue(returned.processed())
        assertTrue(returned.drainSignalNeeded())

        ledger.enqueueReturnedIdle(worker)

        val metrics = ledger.snapshot(0)
        assertEquals(1, metrics.idleWorkers())
        assertEquals(0, metrics.activeWorkers())
    }

    @Test
    fun pollIdleRetiresRequestedWorker() {
        val ledger = ledger(maxSize = 1)
        val workerId = ledger.reserveLaunchWorkerId()
        require(workerId >= 0)
        val worker = newWorker(workerId)
        ledger.registerLaunch()
        ledger.enqueueReturnedIdle(worker)

        worker.requestRetire(WorkerRetirementReason.RETIRE_REQUESTED)
        val retired = mutableListOf<RetiredWorker>()
        val result = ledger.pollIdle(retired, Instant.EPOCH)

        assertTrue(result.isEmpty)
        assertEquals(1, retired.size)
        assertEquals(workerId, retired.single().worker().id())
    }

    @Test
    fun discardLaunchReservationWithoutReservationThrows() {
        val ledger = ledger(maxSize = 1)

        assertFailsWith<IllegalStateException> { ledger.discardLaunchReservation(false) }
    }

    @Test
    fun registerLaunchWithoutReservationThrows() {
        val ledger = ledger(maxSize = 1)

        assertFailsWith<IllegalStateException> {
            ledger.registerLaunch()
        }
    }

    @Test
    fun enqueueRetiredWorkerThrows() {
        val ledger = ledger(maxSize = 1)
        val workerId = ledger.reserveLaunchWorkerId()
        require(workerId >= 0)
        val worker = newWorker(workerId)
        ledger.registerLaunch()
        worker.requestRetire(WorkerRetirementReason.RETIRE_REQUESTED)

        assertFailsWith<IllegalStateException> {
            ledger.enqueueReturnedIdle(worker)
        }
    }

    @Test
    fun retireReturnedWorkerWithoutAllocationThrows() {
        val ledger = ledger(maxSize = 1)

        assertFailsWith<IllegalStateException> { ledger.retireReturnedWorker() }
    }

    private fun ledger(maxSize: Int): CapacityLedger {
        val config =
            ProcessPoolConfig
                .builder(COMMAND)
                .maxSize(maxSize)
                .build()
        return CapacityLedger(config, WorkerRetirementPolicy(config))
    }

    private fun newWorker(id: Int): PoolWorker =
        PoolWorker(
            id,
            TestSession(),
            ExecutionOptions.builder().build(),
            Instant.EPOCH,
        )

    private class TestSession : InteractiveSession {
        override fun stdin(): OutputStream = OutputStream.nullOutputStream()

        override fun stdout(): InputStream = InputStream.nullInputStream()

        override fun stderr(): InputStream = InputStream.nullInputStream()

        override fun onExit(): CompletableFuture<Int> = CompletableFuture.completedFuture(0)

        override fun closeStdin() {}

        override fun sendSignal(signal: ShutdownSignal) {}

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {
        }

        override fun close() {}
    }

    private companion object {
        private val COMMAND = CommandDefinition.of(listOf("ledger-test"))
    }
}
