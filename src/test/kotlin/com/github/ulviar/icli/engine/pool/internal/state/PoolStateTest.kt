package com.github.ulviar.icli.engine.pool.internal.state

import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
import com.github.ulviar.icli.engine.pool.internal.state.AcquireResult
import com.github.ulviar.icli.engine.pool.internal.state.DrainStatus
import com.github.ulviar.icli.engine.pool.internal.state.ReleaseResult
import com.github.ulviar.icli.engine.pool.internal.worker.PoolWorker
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PoolStateTest {
    @Test
    fun maintainsInvariantsAcrossLeaseLifecycle() {
        val clock = MutableClock(Instant.parse("2025-10-29T00:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(2)
                .maxRequestsPerWorker(10)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val reservedIds = state.reserveMinimumIds()
        assertEquals(listOf(1), reservedIds)

        val worker = worker(reservedIds.single(), clock.instant())
        state.onLaunchSuccess(worker)
        assertCounters(state.debugCounters(), allocated = 1, idle = 1, active = 0, launching = 0)

        val leased = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(leased)
        assertEquals(worker.id(), leased.worker().id())
        assertCounters(state.debugCounters(), allocated = 1, idle = 0, active = 1, launching = 0)

        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        assertIs<ReleaseResult.ReturnedToIdle>(releaseResult)

        val counters = state.debugCounters()
        assertCounters(counters, allocated = 1, idle = 1, active = 0, launching = 0)
        assertTrue(counters.allocated >= counters.active + counters.idle)
        assertTrue(counters.allocated + counters.launching <= config.maxSize())
        assertEquals(0, counters.waiters)
        assertFalse(counters.closing)
        assertFalse(counters.terminated)
    }

    @Test
    fun reserveNextForMinimumReservesSequentialIdentifiers() {
        val clock = MutableClock(Instant.parse("2025-10-29T00:30:00Z"))
        val config =
            configBuilder(clock)
                .minSize(2)
                .maxSize(3)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val firstReservation = state.reserveNextForMinimum()
        assertTrue(firstReservation.isPresent)
        val firstId = firstReservation.orElseThrow()
        assertEquals(1, firstId)

        state.onLaunchFailure(true)

        val secondReservation = state.reserveNextForMinimum()
        assertTrue(secondReservation.isPresent)
        val secondId = secondReservation.orElseThrow()
        assertEquals(2, secondId)

        val secondWorker = worker(secondId, clock.instant())
        state.onLaunchSuccess(secondWorker)

        val thirdReservation = state.reserveNextForMinimum()
        assertTrue(thirdReservation.isPresent)
        val thirdId = thirdReservation.orElseThrow()
        assertEquals(3, thirdId)

        val thirdWorker = worker(thirdId, clock.instant())
        state.onLaunchSuccess(thirdWorker)

        val noneRemaining = state.reserveNextForMinimum()
        assertTrue(noneRemaining.isEmpty)

        val counters = state.debugCounters()
        assertCounters(counters, allocated = 2, idle = 2, active = 0, launching = 0)
    }

    @Test
    fun reserveNextForMinimumAccountsForFailedLaunches() {
        val clock = MutableClock(Instant.parse("2025-10-29T01:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(2)
                .maxSize(3)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val firstReservations = state.reserveMinimumIds()
        assertEquals(listOf(1, 2), firstReservations)

        val workerOne = worker(firstReservations.first(), clock.instant())
        state.onLaunchSuccess(workerOne)
        state.onLaunchFailure(true)

        val secondReservations = state.reserveMinimumIds()
        assertEquals(listOf(3), secondReservations)

        val workerThree = worker(secondReservations.single(), clock.instant())
        state.onLaunchSuccess(workerThree)

        assertTrue(state.reserveMinimumIds().isEmpty())
        val counters = state.debugCounters()
        assertCounters(counters, allocated = 2, idle = 2, active = 0, launching = 0)
    }

    @Test
    fun releaseRetiresWhenReuseLimitReached() {
        val clock = MutableClock(Instant.parse("2025-10-29T02:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .maxRequestsPerWorker(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, clock.instant())
        state.onLaunchSuccess(worker)

        val leased = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(leased)

        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        val retired = assertIs<ReleaseResult.Retired>(releaseResult).retired()

        assertEquals(workerId, retired.worker().id())
        assertEquals(WorkerRetirementReason.REUSE_LIMIT_REACHED, retired.reason())
        assertCounters(state.debugCounters(), allocated = 0, idle = 0, active = 0, launching = 0)
    }

    @Test
    fun releaseRetiresWhenLifetimeExceeded() {
        val initial = Instant.parse("2025-10-29T03:00:00Z")
        val clock = MutableClock(initial)
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .maxRequestsPerWorker(5)
                .maxWorkerLifetime(Duration.ofMinutes(5))
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, initial)
        state.onLaunchSuccess(worker)

        val leased = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(leased)

        clock.advance(Duration.ofMinutes(6))
        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        val retired = assertIs<ReleaseResult.Retired>(releaseResult).retired()

        assertEquals(workerId, retired.worker().id())
        assertEquals(WorkerRetirementReason.LIFETIME_EXCEEDED, retired.reason())
    }

    @Test
    fun acquireRetiresIdleWorkerWhenIdleTimeoutExceeded() {
        val initial = Instant.parse("2025-10-29T04:00:00Z")
        val clock = MutableClock(initial)
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(2)
                .maxRequestsPerWorker(10)
                .maxIdleTime(Duration.ofSeconds(1))
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, initial)
        state.onLaunchSuccess(worker)

        val leased = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(leased)

        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        assertIs<ReleaseResult.ReturnedToIdle>(releaseResult)

        clock.advance(Duration.ofSeconds(2))
        val acquireOutcome = state.acquire(0, true)
        val retiredReasons = acquireOutcome.retired().map { it.reason() }
        assertTrue(WorkerRetirementReason.IDLE_TIMEOUT in retiredReasons)
    }

    @Test
    fun acquireRejectsNewLeasesAfterClosingEvenWhenIdle() {
        val clock = MutableClock(Instant.parse("2025-10-29T04:15:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, clock.instant())
        state.onLaunchSuccess(worker)

        assertTrue(state.markClosing())

        val outcome = state.acquire(0, false)
        val failure = assertIs<AcquireResult.Failed>(outcome)
        assertEquals("Process pool is shutting down", failure.error().message)
        assertTrue(failure.retired().isEmpty())

        val counters = state.debugCounters()
        assertCounters(counters, allocated = 1, idle = 1, active = 0, launching = 0)
        assertTrue(counters.closing)
    }

    @Test
    fun reserveNextForMinimumStopsWhenClosing() {
        val clock = MutableClock(Instant.parse("2025-10-29T04:20:00Z"))
        val config =
            configBuilder(clock)
                .minSize(3)
                .maxSize(3)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val firstId = state.reserveSingleWorkerId()
        val worker = worker(firstId, clock.instant())
        state.onLaunchSuccess(worker)

        assertTrue(state.markClosing())
        assertTrue(state.reserveNextForMinimum().isEmpty)

        val counters = state.debugCounters()
        assertCounters(counters, allocated = 1, idle = 1, active = 0, launching = 0)
        assertTrue(counters.closing)
    }

    @Test
    fun drainCompletesWhileLaunchReservationsOutstanding() {
        val clock = MutableClock(Instant.parse("2025-10-29T04:24:00Z"))
        val config =
            configBuilder(clock)
                .minSize(2)
                .maxSize(4)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val reservedIds = state.reserveMinimumIds()
        val reserved = reservedIds.size
        assertEquals(2, reserved)

        assertTrue(state.markClosing())
        val retiring = mutableListOf<PoolWorker>()
        val status = state.drain(0, retiring)

        assertTrue(status.completed())
        assertTrue(status.terminatedNow())
        assertTrue(retiring.isEmpty())

        val countersAfterDrain = state.debugCounters()
        assertEquals(reserved, countersAfterDrain.launching)

        reservedIds.forEach { state.onLaunchFailure(false) }
        val countersAfterCleanup = state.debugCounters()
        assertCounters(countersAfterCleanup, allocated = 0, idle = 0, active = 0, launching = 0)
    }

    @Test
    fun interruptedWaiterRequeuesAssignedWorker() {
        val initial = Instant.parse("2025-10-29T04:30:00Z")
        val clock = MutableClock(initial)
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, initial)
        state.onLaunchSuccess(worker)

        val leased = state.acquire(0, true)
        val activeLease = assertIs<AcquireResult.Leased>(leased)

        val completion = CountDownLatch(1)
        val resultHolder = AtomicReference<AcquireResult?>()
        val interrupted = AtomicBoolean(false)

        val waiterThread =
            Thread
                .ofVirtual()
                .unstarted {
                    try {
                        val result = state.acquire(System.nanoTime() + TimeUnit.SECONDS.toNanos(5), true)
                        resultHolder.set(result)
                    } finally {
                        interrupted.set(Thread.currentThread().isInterrupted)
                        completion.countDown()
                    }
                }
        waiterThread.start()

        waitUntilTrue { state.debugCounters().waiters == 1 }
        waitUntilTrue(Duration.ofSeconds(2)) {
            val state = waiterThread.state
            state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
        }

        waiterThread.interrupt()

        val releasePlan = state.beginRelease(activeLease.worker(), clock.instant())
        val releaseResult = state.completeRelease(activeLease.worker(), clock.instant(), releasePlan)
        assertIs<ReleaseResult.Assigned>(releaseResult)

        assertTrue(completion.await(1, TimeUnit.SECONDS), "Waiter thread did not finish")

        val result = resultHolder.get()
        assertNotNull(result)
        when (result) {
            is AcquireResult.Leased -> {
                val retryPlan = state.beginRelease(result.worker(), clock.instant())
                state.completeRelease(result.worker(), clock.instant(), retryPlan)
            }
            is AcquireResult.Failed -> {
                // expected path; worker should have been reclaimed
            }
            else -> fail("Unexpected acquire outcome: ${result::class.simpleName}")
        }
        assertTrue(interrupted.get(), "Interrupt flag must be preserved")

        val replacement = state.acquire(0, true)
        val replacementLease = assertIs<AcquireResult.Leased>(replacement)
        assertEquals(workerId, replacementLease.worker().id())

        val replacementPlan = state.beginRelease(replacementLease.worker(), clock.instant())
        val replacementResult = state.completeRelease(replacementLease.worker(), clock.instant(), replacementPlan)
        assertIs<ReleaseResult.ReturnedToIdle>(replacementResult)

        val counters = state.debugCounters()
        assertCounters(counters, allocated = 1, idle = 1, active = 0, launching = 0)
    }

    @Test
    fun drainCollectsIdleWorkersAndTerminatesPool() {
        val clock = MutableClock(Instant.parse("2025-10-29T05:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, clock.instant())
        state.onLaunchSuccess(worker)

        assertTrue(state.markClosing())
        val retiring = mutableListOf<PoolWorker>()
        val status = state.drain(0, retiring)

        assertTrue(status.completed())
        assertTrue(status.terminatedNow())
        assertEquals(listOf(workerId), retiring.map(PoolWorker::id))
        assertTrue(state.debugCounters().terminated)
    }

    @Test
    fun drainTimesOutWhenActiveWorkersPersist() {
        val clock = MutableClock(Instant.parse("2025-10-29T06:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, clock.instant())
        state.onLaunchSuccess(worker)

        val lease = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(lease)

        val retiring = mutableListOf<PoolWorker>()
        val deadline = System.nanoTime()
        val status = state.drain(deadline, retiring)

        assertFalse(status.completed())
        assertFalse(status.terminatedNow())
        assertTrue(retiring.isEmpty())

        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        assertIs<ReleaseResult.ReturnedToIdle>(releaseResult)
    }

    @Test
    fun drainInterruptionRestoresInterruptFlag() {
        val clock = MutableClock(Instant.parse("2025-10-29T07:00:00Z"))
        val config =
            configBuilder(clock)
                .minSize(1)
                .maxSize(1)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        val workerId = state.reserveSingleWorkerId()
        val worker = worker(workerId, clock.instant())
        state.onLaunchSuccess(worker)

        val lease = state.acquire(0, true)
        assertIs<AcquireResult.Leased>(lease)

        val interrupted = AtomicBoolean(false)
        val statusHolder = AtomicReference<DrainStatus?>()
        val completion = CountDownLatch(1)

        val thread =
            Thread
                .ofVirtual()
                .unstarted {
                    val retiring = mutableListOf<PoolWorker>()
                    val status = state.drain(0, retiring)
                    interrupted.set(Thread.currentThread().isInterrupted)
                    statusHolder.set(status)
                    completion.countDown()
                }
        thread.start()

        TimeUnit.MILLISECONDS.sleep(50)
        thread.interrupt()
        assertTrue(completion.await(1, TimeUnit.SECONDS), "Drain thread did not finish")

        val status = statusHolder.get()
        assertNotNull(status)
        assertFalse(status.completed())
        assertFalse(status.terminatedNow())
        assertTrue(interrupted.get())

        val releasePlan = state.beginRelease(worker, clock.instant())
        val releaseResult = state.completeRelease(worker, clock.instant(), releasePlan)
        assertIs<ReleaseResult.ReturnedToIdle>(releaseResult)
    }

    @Test
    fun invariantChecksEnabledByDefault() {
        val clock = MutableClock(Instant.parse("2025-10-30T00:00:00Z"))
        val config = configBuilder(clock).build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        corruptLedger(state)

        assertFailsWith<IllegalStateException> { state.assertInvariantsGuarded() }
    }

    @Test
    fun invariantChecksCanBeDisabled() {
        val clock = MutableClock(Instant.parse("2025-10-30T01:00:00Z"))
        val config =
            configBuilder(clock)
                .invariantChecksEnabled(false)
                .build()
        val state = PoolState(config, WorkerRetirementPolicy(config))

        corruptLedger(state)

        state.assertInvariantsGuarded()
    }

    private fun assertCounters(
        counters: PoolStateCounters,
        allocated: Int,
        idle: Int,
        active: Int,
        launching: Int,
    ) {
        assertEquals(allocated, counters.allocated)
        assertEquals(idle, counters.idle)
        assertEquals(active, counters.active)
        assertEquals(launching, counters.launching)
    }

    private fun worker(
        id: Int,
        createdAt: Instant,
    ): PoolWorker =
        PoolWorker(
            id,
            TestInteractiveSession(),
            ExecutionOptions.builder().idleTimeout(Duration.ZERO).build(),
            createdAt,
        )

    private fun configBuilder(clock: Clock): ProcessPoolConfig.Builder =
        ProcessPoolConfig
            .builder(COMMAND)
            .clock(clock)

    private fun waitUntilTrue(
        timeout: Duration = Duration.ofSeconds(1),
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        fail("Condition not satisfied within timeout")
    }

    private fun PoolState.reserveMinimumIds(): List<Int> {
        val ids = mutableListOf<Int>()
        while (true) {
            val reservation = reserveNextForMinimum()
            if (reservation.isEmpty) {
                break
            }
            ids += reservation.orElseThrow()
        }
        return ids
    }

    private fun PoolState.reserveSingleWorkerId(): Int = reserveNextForMinimum().orElseThrow()

    private class MutableClock(
        private var current: Instant,
        private val zoneId: ZoneId = ZoneId.of("UTC"),
    ) : Clock() {
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

        override fun getZone(): ZoneId = zoneId

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private fun corruptLedger(state: PoolState) {
        val ledgerField = PoolState::class.java.getDeclaredField("ledger")
        ledgerField.isAccessible = true
        val ledger = ledgerField.get(state)
        val allocatedField = ledger.javaClass.getDeclaredField("allocatedWorkers")
        allocatedField.isAccessible = true
        allocatedField.setInt(ledger, -1)
    }

    private class TestInteractiveSession : InteractiveSession {
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
            exit.complete(0)
        }
    }

    private companion object {
        private val COMMAND = CommandDefinition.of(listOf("pool-state-test"))
    }
}
