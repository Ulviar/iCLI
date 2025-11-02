package com.github.ulviar.icli.engine.pool.api

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener
import com.github.ulviar.icli.engine.pool.api.hooks.RequestTimeoutScheduler
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine
import com.github.ulviar.icli.testing.TestProcessCommand
import org.junit.jupiter.api.AfterEach
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessPoolIntegrationTest {
    private val engine = StandardProcessEngine()
    private var pool: ProcessPool? = null

    @AfterEach
    fun tearDown() {
        pool?.close()
        pool?.drain(Duration.ofSeconds(5))
        pool = null
    }

    @Test
    fun `interactive worker replenishes after reuse cap`() {
        val diagnostics = RecordingDiagnostics()
        pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(testProcessCommand("--interactive"))
                    .minSize(1)
                    .maxSize(1)
                    .maxRequestsPerWorker(1)
                    .requestTimeout(Duration.ofSeconds(30))
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        val firstLease = pool!!.acquire(Duration.ofSeconds(2))
        val firstSession = firstLease.session()
        val stdout = reader(firstSession)
        assertEquals("READY", stdout.readLine())

        firstSession.stdin().write("ping\n".toByteArray())
        firstSession.stdin().flush()
        assertEquals("OUT:ping", stdout.readLine())
        firstLease.close()

        val metricsAfterFirst = pool!!.snapshot()
        assertEquals(1, metricsAfterFirst.idleWorkers())

        val secondLease = pool!!.acquire(Duration.ofSeconds(2))
        val secondSession = secondLease.session()
        val stdoutSecond = reader(secondSession)
        assertEquals("READY", stdoutSecond.readLine())
        secondSession.stdin().write("pong\n".toByteArray())
        secondSession.stdin().flush()
        assertEquals("OUT:pong", stdoutSecond.readLine())

        val firstWorkerId = diagnostics.leases.first().workerId
        val secondWorkerId = diagnostics.leases.last().workerId
        assertNotEquals(firstWorkerId, secondWorkerId)

        secondLease.close()
    }

    @Test
    fun `request timeout retires stalled worker`() {
        val diagnostics = RecordingDiagnostics()
        val scheduler = ManualRequestTimeoutScheduler()
        pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(testProcessCommand("--sleep-ms", "1000"))
                    .minSize(1)
                    .maxSize(1)
                    .maxRequestsPerWorker(2)
                    .requestTimeout(Duration.ofMillis(150))
                    .requestTimeoutSchedulerFactory { scheduler }
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        val lease = pool!!.acquire(Duration.ofSeconds(2))
        val workerId = lease.scope().workerId()
        val requestId = lease.scope().requestId()
        scheduler.triggerTimeout(workerId)
        lease.close()

        assertTrue(diagnostics.timeoutRequests.isNotEmpty())
        val metricsAfterTimeout = pool!!.snapshot()
        assertEquals(1, metricsAfterTimeout.idleWorkers())
        assertTrue(metricsAfterTimeout.totalRetirements() >= 1)
        assertTrue(scheduler.triggered(requestId))
    }

    private fun testProcessCommand(vararg args: String): CommandDefinition =
        CommandDefinition
            .builder()
            .command(TestProcessCommand.command(*args))
            .terminalPreference(TerminalPreference.AUTO)
            .build()

    private fun reader(session: com.github.ulviar.icli.engine.InteractiveSession): BufferedReader =
        BufferedReader(InputStreamReader(session.stdout()))

    private class RecordingDiagnostics : PoolDiagnosticsListener {
        val leases = mutableListOf<LeaseEvent>()
        val timeoutRequests = mutableListOf<UUID>()

        override fun leaseAcquired(workerId: Int) {
            leases += LeaseEvent(workerId)
        }

        override fun leaseTimedOut(
            workerId: Int,
            requestId: UUID,
        ) {
            timeoutRequests += requestId
        }
    }

    private data class LeaseEvent(
        val workerId: Int,
    )

    @Suppress("UNUSED_PARAMETER")
    private class ManualRequestTimeoutScheduler : RequestTimeoutScheduler {
        private val callbacks = ConcurrentHashMap<Int, TimeoutEntry>()
        private val fired = ConcurrentHashMap.newKeySet<UUID>()

        override fun schedule(
            workerId: Int,
            requestId: UUID,
            timeout: Duration,
            onTimeout: Runnable,
        ) {
            callbacks[workerId] = TimeoutEntry(requestId, onTimeout)
        }

        override fun cancel(workerId: Int) {
            callbacks.remove(workerId)
        }

        override fun complete(
            workerId: Int,
            requestId: UUID,
        ): Boolean {
            val entry = callbacks[workerId] ?: return false
            if (entry.requestId != requestId) {
                return false
            }
            callbacks.remove(workerId)
            fired += requestId
            return true
        }

        override fun close() {
            callbacks.clear()
        }

        fun triggerTimeout(workerId: Int) {
            callbacks[workerId]?.onTimeout?.run()
        }

        fun triggered(requestId: UUID): Boolean = fired.contains(requestId)

        private data class TimeoutEntry(
            val requestId: UUID,
            val onTimeout: Runnable,
        )
    }
}
