package com.github.ulviar.icli.engine.pool.internal.state

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerRetirementPolicyTest {
    private lateinit var policy: WorkerRetirementPolicy
    private lateinit var now: Instant

    @BeforeTest
    fun setUp() {
        now = Instant.parse("2025-10-28T00:00:00Z")
        policy =
            WorkerRetirementPolicy(
                ProcessPoolConfig
                    .builder(COMMAND)
                    .maxRequestsPerWorker(2)
                    .maxWorkerLifetime(Duration.ofMinutes(30))
                    .maxIdleTime(Duration.ofMinutes(5))
                    .build(),
            )
    }

    @Test
    fun reuseThresholdTriggersRetirement() {
        val worker = worker(createdAt = now)
        markReuse(worker, now)
        markReuse(worker, now)

        val reason = policy.shouldRetire(worker, now)
        assertEquals(
            WorkerRetirementReason.REUSE_LIMIT_REACHED,
            reason.orElseThrow { IllegalStateException("Expected reuse retirement reason") },
        )
    }

    @Test
    fun lifetimeThresholdTriggersRetirement() {
        val created = now.minus(Duration.ofMinutes(31))
        val worker = worker(createdAt = created)

        val reason = policy.shouldRetire(worker, now)
        assertEquals(
            WorkerRetirementReason.LIFETIME_EXCEEDED,
            reason.orElseThrow { IllegalStateException("Expected lifetime retirement reason") },
        )
    }

    @Test
    fun idleThresholdTriggersRetirement() {
        val worker = worker(createdAt = now.minus(Duration.ofMinutes(10)))
        markReuse(worker, now.minus(Duration.ofMinutes(6)))

        val reason = policy.shouldRetireForIdle(worker, now)
        assertEquals(
            WorkerRetirementReason.IDLE_TIMEOUT,
            reason.orElseThrow { IllegalStateException("Expected idle retirement reason") },
        )
    }

    @Test
    fun belowThresholdsKeepsWorker() {
        val worker = worker(createdAt = now.minus(Duration.ofMinutes(1)))
        markReuse(worker, now.minus(Duration.ofMinutes(1)))

        val lifetimeReason = policy.shouldRetire(worker, now)
        val idleReason = policy.shouldRetireForIdle(worker, now)

        assertTrue(lifetimeReason.isEmpty, "Expected lifetime reason to be empty")
        assertTrue(idleReason.isEmpty, "Expected idle reason to be empty")
    }

    private fun markReuse(
        worker: PoolWorker,
        timestamp: Instant,
    ) {
        worker.markLeased(UUID.randomUUID())
        worker.markReturned(timestamp)
    }

    private fun worker(createdAt: Instant): PoolWorker =
        PoolWorker(
            1,
            FakeInteractiveSession(),
            ExecutionOptions.builder().idleTimeout(Duration.ZERO).build(),
            createdAt,
        )

    private class FakeInteractiveSession : InteractiveSession {
        private val closed = AtomicBoolean(false)
        private val exit = CompletableFuture<Int>()

        override fun stdin(): OutputStream = OutputStream.nullOutputStream()

        override fun stdout(): InputStream = InputStream.nullInputStream()

        override fun stderr(): InputStream = InputStream.nullInputStream()

        override fun onExit(): CompletableFuture<Int> = exit

        override fun closeStdin() {}

        override fun sendSignal(signal: ShutdownSignal) {}

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {}

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                exit.complete(0)
            }
        }
    }

    private companion object {
        private val COMMAND = CommandDefinition.of(listOf("noop"))
    }
}
