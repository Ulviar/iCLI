package com.github.ulviar.icli.engine.pool.internal.lease

import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultWorkerLeaseTest {
    @Test
    fun closeIsIdempotentAndResetsAfterCloseAreIgnored() {
        val callbacks = RecordingCallbacks()
        val lease = newLease(callbacks)
        val scope = lease.scope() as DefaultLeaseScope
        val reset = ResetRequest.manual(scope.requestId())

        assertEquals(1, callbacks.registered.size)
        assertEquals(
            scope.requestId(),
            callbacks.registered
                .first()
                .second
                .requestId(),
        )

        lease.reset(reset)
        lease.close()
        lease.reset(reset)
        lease.close()

        assertEquals(listOf(reset), callbacks.resets)
        assertEquals(listOf(scope.requestId()), callbacks.releases)
        assertSame(scope, callbacks.releasesScope)
        assertEquals(callbacks.workerId, callbacks.registered.first().first)
    }

    private fun newLease(callbacks: RecordingCallbacks): DefaultWorkerLease {
        val session = TestInteractiveSession()
        val options = ExecutionOptions.builder().idleTimeout(Duration.ZERO).build()
        val worker = PoolWorker(1, session, options, Instant.parse("2025-10-29T00:00:00Z"))
        val scope = DefaultLeaseScope(worker, Instant.parse("2025-10-29T00:00:01Z"))
        return DefaultWorkerLease(callbacks, worker, scope)
    }

    private class RecordingCallbacks : LeaseCallbacks {
        val registered = mutableListOf<Pair<Int, DefaultLeaseScope>>()
        val resets = mutableListOf<ResetRequest>()
        val releases = mutableListOf<UUID>()
        var releasesScope: DefaultLeaseScope? = null
        var workerId: Int = -1

        override fun registerActiveLease(
            workerId: Int,
            scope: DefaultLeaseScope,
        ) {
            this.workerId = workerId
            registered += workerId to scope
        }

        override fun resetLease(
            worker: PoolWorker,
            scope: DefaultLeaseScope,
            request: ResetRequest,
        ) {
            resets += request
        }

        override fun releaseLease(
            worker: PoolWorker,
            scope: DefaultLeaseScope,
            requestId: UUID,
        ) {
            releases += requestId
            releasesScope = scope
        }
    }

    private class TestInteractiveSession : InteractiveSession {
        private val exit = CompletableFuture<Int>()
        private val closed = AtomicBoolean(false)

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
}
