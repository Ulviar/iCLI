package com.github.ulviar.icli.engine.pool.internal.runtime

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
import com.github.ulviar.icli.engine.pool.api.hooks.ResetHook
import com.github.ulviar.icli.engine.pool.api.hooks.ResetOutcome
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import com.github.ulviar.icli.engine.pool.internal.lease.DefaultLeaseScope
import com.github.ulviar.icli.engine.pool.internal.state.WorkerRetirementPolicy
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetHookRunnerTest {
    private val session =
        object : InteractiveSession {
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

    @Test
    fun retireRequestedByHook() {
        val diagnostics = RecordingDiagnostics()
        val runner =
            ResetHookRunner(
                listOf(
                    ResetHook { _: InteractiveSession, _: LeaseScope, _: ResetRequest ->
                        ResetOutcome.RETIRE
                    },
                ),
                diagnostics,
            )
        val worker = worker()
        val scope = DefaultLeaseScope(worker, Instant.now())
        val result = runner.run(worker, scope, ResetRequest.leaseCompleted(UUID.randomUUID()))

        assertTrue(result.retire())
        val retireReason = (result as RetireDecision.Retire).reason()
        assertEquals(WorkerRetirementReason.RESET_HOOK_REQUESTED, retireReason)
    }

    @Test
    fun retireOnException() {
        val diagnostics = RecordingDiagnostics()
        val runner =
            ResetHookRunner(
                listOf(
                    ResetHook { _: InteractiveSession, _: LeaseScope, _: ResetRequest ->
                        throw IllegalStateException("boom")
                    },
                ),
                diagnostics,
            )
        val worker = worker()
        val scope = DefaultLeaseScope(worker, Instant.now())
        val result = runner.run(worker, scope, ResetRequest.manual(UUID.randomUUID()))

        assertTrue(result.retire())
        val retireFailureReason = (result as RetireDecision.Retire).reason()
        assertEquals(WorkerRetirementReason.RESET_HOOK_FAILURE, retireFailureReason)
        assertTrue(diagnostics.failures.isNotEmpty())
    }

    @Test
    fun keepWhenNoHooksRetire() {
        val diagnostics = RecordingDiagnostics()
        val runner = ResetHookRunner(listOf(), diagnostics)
        val worker = worker()
        val scope = DefaultLeaseScope(worker, Instant.now())

        val result = runner.run(worker, scope, ResetRequest.leaseCompleted(UUID.randomUUID()))

        assertFalse(result.retire())
    }

    @Test
    fun keepsExistingRetirementReasonWhenHookRequestsRetire() {
        val diagnostics = RecordingDiagnostics()
        val runner =
            ResetHookRunner(
                listOf(
                    ResetHook { _: InteractiveSession, _: LeaseScope, _: ResetRequest ->
                        ResetOutcome.RETIRE
                    },
                ),
                diagnostics,
            )
        val worker = worker()
        worker.requestRetire(WorkerRetirementReason.REUSE_LIMIT_REACHED)
        val scope = DefaultLeaseScope(worker, Instant.now())

        val result = runner.run(worker, scope, ResetRequest.leaseCompleted(UUID.randomUUID()))

        assertTrue(result.retire())
        val retireReason = (result as RetireDecision.Retire).reason()
        assertEquals(WorkerRetirementReason.REUSE_LIMIT_REACHED, retireReason)
    }

    @Test
    fun honoursExistingRetirementWhenHooksKeep() {
        val diagnostics = RecordingDiagnostics()
        val runner =
            ResetHookRunner(
                listOf(
                    ResetHook { _: InteractiveSession, _: LeaseScope, _: ResetRequest ->
                        ResetOutcome.CONTINUE
                    },
                ),
                diagnostics,
            )
        val worker = worker()
        worker.requestRetire(WorkerRetirementReason.POOL_CLOSING)
        val scope = DefaultLeaseScope(worker, Instant.now())

        val result = runner.run(worker, scope, ResetRequest.manual(UUID.randomUUID()))

        assertTrue(result.retire())
        val retireReason = (result as RetireDecision.Retire).reason()
        assertEquals(WorkerRetirementReason.POOL_CLOSING, retireReason)
    }

    @Test
    fun reportsDiagnosticsForHookFailureWithWorkerId() {
        val diagnostics = RecordingDiagnostics()
        val runner =
            ResetHookRunner(
                listOf(
                    ResetHook { _: InteractiveSession, _: LeaseScope, _: ResetRequest ->
                        throw IllegalArgumentException("reset failed")
                    },
                ),
                diagnostics,
            )
        val worker = worker()
        val scope = DefaultLeaseScope(worker, Instant.now())

        runner.run(worker, scope, ResetRequest.manual(UUID.randomUUID()))

        assertEquals(listOf(worker.id()), diagnostics.failureWorkerIds)
        assertEquals(1, diagnostics.failures.size)
    }

    private fun worker(): PoolWorker =
        PoolWorker(
            1,
            session,
            ExecutionOptions.builder().idleTimeout(Duration.ZERO).build(),
            Instant.now(),
        )

    private class RecordingDiagnostics : PoolDiagnosticsListener {
        val failures = mutableListOf<Throwable>()
        val failureWorkerIds = mutableListOf<Int>()

        override fun workerFailed(
            workerId: Int,
            error: Throwable,
        ) {
            failureWorkerIds += workerId
            failures += error
        }
    }
}
