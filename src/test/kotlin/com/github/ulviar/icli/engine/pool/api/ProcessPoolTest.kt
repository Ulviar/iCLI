package com.github.ulviar.icli.engine.pool.api

import com.github.ulviar.icli.client.ClientScheduler
import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.InteractiveSession
import com.github.ulviar.icli.engine.ProcessEngine
import com.github.ulviar.icli.engine.ProcessResult
import com.github.ulviar.icli.engine.ShutdownSignal
import com.github.ulviar.icli.engine.pool.api.PoolMetrics
import com.github.ulviar.icli.engine.pool.api.WorkerRetirementReason
import com.github.ulviar.icli.engine.pool.api.hooks.RequestTimeoutScheduler
import com.github.ulviar.icli.engine.pool.api.hooks.ResetOutcome
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessPoolTest {
    @Test
    fun prewarmsMinWorkers() {
        val engine = FakeProcessEngine()
        val config =
            ProcessPoolConfig
                .builder(COMMAND)
                .minSize(2)
                .maxSize(2)
                .build()
        val pool = ProcessPool.create(engine, config)

        try {
            val metrics = pool.snapshot()
            assertEquals(2, metrics.idleWorkers())
            assertEquals(0, metrics.activeWorkers())
            assertEquals(2, engine.createdCount())
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun acquiresPreferredWorkerWhenIdle() {
        val engine = FakeProcessEngine()
        val pool = ProcessPool.create(engine, ProcessPoolConfig.builder(COMMAND).maxSize(1).build())

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val workerId = lease.scope().workerId()
            lease.close()

            val preferred = pool.acquireWithPreference(PreferredWorker.specific(workerId), Duration.ofSeconds(1))
            assertEquals(workerId, preferred.scope().workerId())
            preferred.close()
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun fallsBackWhenPreferredWorkerUnavailable() {
        val engine = FakeProcessEngine()
        val pool = ProcessPool.create(engine, ProcessPoolConfig.builder(COMMAND).maxSize(1).build())

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val workerId = lease.scope().workerId()
            lease.close()

            val preferred =
                pool.acquireWithPreference(PreferredWorker.specific(workerId + 5), Duration.ofSeconds(1))
            assertEquals(workerId, preferred.scope().workerId())
            preferred.close()
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun metricsPublishingSkipsConsecutiveDuplicates() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        try {
            assertNoConsecutiveDuplicateMetrics(diagnostics.metricsSnapshots)

            diagnostics.metricsSnapshots.clear()
            val lease = pool.acquire(Duration.ofSeconds(1))
            lease.close()

            assertNoConsecutiveDuplicateMetrics(diagnostics.metricsSnapshots)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun ensureMinimumSizeReleasesReservationsAfterFailedLaunch() {
        val engine = FailsOnceProcessEngine(failAttempt = 2)
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(3)
                    .maxSize(3)
                    .build(),
            )

        try {
            assertEquals(1, engine.createdCount(), "Only the first worker should be live after initial failure")

            val lease = pool.acquire(Duration.ofSeconds(1))
            lease.close()

            assertEquals(3, engine.createdCount(), "Pool must replenish to min size after failure")
        } finally {
            pool.close()
            pool.drain(Duration.ofSeconds(1))
        }
    }

    @Test
    fun prewarmBackoffAppliesAfterRepeatedFailures() {
        val delays = mutableListOf<Duration>()
        ProcessPool.setPrewarmSleeperForTests { duration -> delays += duration }
        val engine = AlwaysFailProcessEngine()
        var pool: ProcessPool? = null
        try {
            pool =
                ProcessPool.create(
                    engine,
                    ProcessPoolConfig
                        .builder(COMMAND)
                        .minSize(1)
                        .maxSize(1)
                        .build(),
                )

            val created = requireNotNull(pool)

            assertEquals(
                listOf(Duration.ofMillis(50)),
                delays,
                "Initial prewarm failure should schedule base backoff",
            )

            assertFailsWith<ServiceUnavailableException> { created.acquire(Duration.ofSeconds(1)) }
            assertTrue(delays.size >= 2, "Subsequent failures should schedule additional backoff delays")
            assertTrue(
                delays.drop(1).all { it >= delays.first() },
                "Backoff delays should be non-decreasing",
            )
        } finally {
            ProcessPool.resetPrewarmSleeperForTests()
            pool?.close()
            pool?.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun drainMarksClosingWhenCalledDirectly() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        val completed = pool.drain(Duration.ofSeconds(1))
        assertTrue(completed, "Drain should complete when idle workers exist")
        assertTrue(diagnostics.draining, "Drain must mark the pool as closing")
        assertTrue(diagnostics.terminated, "Drain must report termination on completion")

        assertFailsWith<ServiceUnavailableException> {
            pool.acquire(Duration.ofMillis(50))
        }
    }

    @Test
    fun reusesWorkerUntilReuseCap() {
        val engine = FakeProcessEngine()
        val config =
            ProcessPoolConfig
                .builder(COMMAND)
                .maxRequestsPerWorker(2)
                .build()
        val pool = ProcessPool.create(engine, config)

        try {
            val first = pool.acquire(Duration.ofSeconds(1))
            val firstId = (first.session() as FakeInteractiveSession).id
            first.close()

            val second = pool.acquire(Duration.ofSeconds(1))
            val secondId = (second.session() as FakeInteractiveSession).id
            second.close()
            assertEquals(firstId, secondId)

            val third = pool.acquire(Duration.ofSeconds(1))
            val thirdId = (third.session() as FakeInteractiveSession).id
            third.close()
            assertNotEquals(firstId, thirdId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun closeRejectsNewAcquisitions() {
        val engine = FakeProcessEngine()
        val pool = ProcessPool.create(engine, ProcessPoolConfig.builder(COMMAND).build())

        val lease = pool.acquire(Duration.ofSeconds(1))
        pool.close()
        lease.close()

        assertFailsWith<ServiceUnavailableException> {
            pool.acquire(Duration.ofMillis(50))
        }

        pool.drain(Duration.ofMillis(100))
    }

    @Test
    fun acquireUsesConfiguredDefaultTimeout() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(0)
                    .maxSize(1)
                    .leaseTimeout(Duration.ZERO)
                    .build(),
            )

        try {
            assertFailsWith<ServiceUnavailableException> {
                pool.acquire()
            }
            assertEquals(0, engine.createdCount(), "non-blocking default acquire must not launch workers")
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun nonBlockingAcquireFailsWhenNoWorkerReady() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(0)
                    .maxSize(1)
                    .build(),
            )

        try {
            assertFailsWith<ServiceUnavailableException> {
                pool.acquire(Duration.ZERO)
            }
            assertEquals(0, engine.createdCount(), "non-blocking acquire must fail fast when no workers exist")

            val lease = pool.acquire(Duration.ofSeconds(1))
            lease.close()
            assertEquals(1, engine.createdCount(), "pool should launch a worker when blocking acquire succeeds")
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun nonBlockingAcquireSucceedsWhenWorkerIdle() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ZERO)
            val workerId = (lease.session() as FakeInteractiveSession).id
            lease.close()
            assertEquals(1, workerId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun negativeTimeoutRejected() {
        val engine = FakeProcessEngine()
        val pool = ProcessPool.create(engine, ProcessPoolConfig.builder(COMMAND).build())

        try {
            assertFailsWith<IllegalArgumentException> {
                pool.acquire(Duration.ofMillis(-1))
            }
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun idleWorkerPrefersQueuedWaitersOverNewArrivals() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .build(),
            )

        val activeLease = pool.acquire(Duration.ofSeconds(1))
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val acquisitionOrder = CopyOnWriteArrayList<Int>()

        try {
            val firstReady = CompletableFuture<Void>()
            val firstFuture =
                CompletableFuture.supplyAsync({
                    firstReady.complete(null)
                    val lease = pool.acquire(Duration.ofSeconds(5))
                    acquisitionOrder += 0
                    val id = (lease.session() as FakeInteractiveSession).id
                    lease.close()
                    id
                }, executor)

            firstReady.get(1, TimeUnit.SECONDS)

            val secondFuture =
                CompletableFuture.supplyAsync({
                    val lease = pool.acquire(Duration.ofSeconds(5))
                    acquisitionOrder += 1
                    val id = (lease.session() as FakeInteractiveSession).id
                    lease.close()
                    id
                }, executor)

            // allow both waiters to enqueue behind the held worker
            TimeUnit.MILLISECONDS.sleep(50)

            // releasing the active lease should hand the worker to the first queued waiter
            activeLease.close()

            firstFuture.get(1, TimeUnit.SECONDS)
            secondFuture.get(1, TimeUnit.SECONDS)

            assertEquals(listOf(0, 1), acquisitionOrder)
        } finally {
            executor.shutdownNow()
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun closingPoolDoesNotLaunchNewWorkers() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .maxRequestsPerWorker(1)
                    .build(),
            )

        val lease = pool.acquire(Duration.ofSeconds(1))
        assertEquals(1, engine.createdCount())

        pool.close()
        lease.close()

        assertEquals(1, engine.createdCount(), "pool must not prewarm replacements after close()")

        pool.drain(Duration.ofMillis(100))
    }

    @Test
    fun queueDepthZeroFailsFast() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .maxSize(1)
                    .maxQueueDepth(0)
                    .build(),
            )

        val lease = pool.acquire(Duration.ofSeconds(1))

        val exception =
            assertFailsWith<ServiceUnavailableException> {
                pool.acquire(Duration.ofSeconds(1))
            }
        val message = exception.message!!
        assertTrue(message.contains("pending=0"))
        assertTrue(message.contains("capacity=0"))

        lease.close()
        pool.close()
        pool.drain(Duration.ofMillis(100))
    }

    @Test
    fun retiresWorkerWhenLifetimeExceeded() {
        val engine = FakeProcessEngine()
        val clock = MutableClock(Instant.parse("2025-10-28T00:00:00Z"))
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .maxRequestsPerWorker(5)
                    .maxWorkerLifetime(Duration.ofSeconds(5))
                    .clock(clock)
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val initialId = (lease.session() as FakeInteractiveSession).id

            clock.advance(Duration.ofSeconds(6))
            lease.close()

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(initialId, replacementId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun evictsWorkerAfterIdleTimeout() {
        val engine = FakeProcessEngine()
        val clock = MutableClock(Instant.parse("2025-10-28T00:00:00Z"))
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .maxIdleTime(Duration.ofSeconds(5))
                    .clock(clock)
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val initialId = (lease.session() as FakeInteractiveSession).id
            lease.close()

            clock.advance(Duration.ofSeconds(6))

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(initialId, replacementId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun maintainsMinimumSizeAfterRetirement() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .maxRequestsPerWorker(1)
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val firstId = (lease.session() as FakeInteractiveSession).id
            lease.close()

            val metrics = pool.snapshot()
            assertEquals(1, metrics.idleWorkers())

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(firstId, replacementId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun metricsTrackTotalLeasesServed() {
        val engine = FakeProcessEngine()
        val pool = ProcessPool.create(engine, ProcessPoolConfig.builder(COMMAND).maxSize(1).build())

        try {
            val first = pool.acquire(Duration.ofSeconds(1))
            val duringFirst = pool.snapshot()
            assertEquals(1, duringFirst.activeWorkers())
            first.close()

            val afterFirst = pool.snapshot()
            assertEquals(1, afterFirst.totalLeasesServed())

            val second = pool.acquire(Duration.ofSeconds(1))
            second.close()
            val afterSecond = pool.snapshot()
            assertEquals(2, afterSecond.totalLeasesServed())
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun diagnosticsCaptureLifecycleEvents() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val workerId = lease.scope().workerId()
            lease.close()

            pool.close()
            pool.drain(Duration.ofSeconds(1))

            assertTrue(
                diagnostics.workerCreatedIds.contains(workerId),
                "diagnostics must record workerCreated for $workerId",
            )
            assertTrue(
                diagnostics.leaseAcquiredWorkers.contains(workerId),
                "diagnostics must record leaseAcquired for $workerId",
            )
            assertTrue(
                diagnostics.leaseReleasedWorkers.contains(workerId),
                "diagnostics must record leaseReleased for $workerId",
            )
            val retireReasons =
                diagnostics.retiredWorkers
                    .filter { it.first == workerId }
                    .map { it.second }
            assertTrue(
                retireReasons.contains(WorkerRetirementReason.DRAIN),
                "drain should retire worker with DRAIN reason",
            )
            assertTrue(
                diagnostics.metricsSnapshots.isNotEmpty(),
                "metricsUpdated should be invoked at least once",
            )
            assertTrue(diagnostics.draining)
            assertTrue(diagnostics.terminated)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun leaseTimeoutRetiresWorker() {
        val engine = FakeProcessEngine()
        val scheduler = ManualRequestTimeoutScheduler()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .requestTimeout(Duration.ofMillis(50))
                    .maxRequestsPerWorker(2)
                    .requestTimeoutSchedulerFactory { scheduler }
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val session = lease.session() as FakeInteractiveSession
            val workerId = lease.scope().workerId()
            val requestId = lease.scope().requestId()

            scheduler.triggerTimeout(workerId)

            assertTrue(scheduler.triggered(requestId))

            lease.close()

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(session.id, replacementId)
            assertTrue(session.signalCount() > 0 || session.isClosed())
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun leaseTimeoutInvokesResetHooksWithTimeoutReason() {
        val engine = FakeProcessEngine()
        val scheduler = ManualRequestTimeoutScheduler()
        val resetRequests = mutableListOf<ResetRequest>()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .requestTimeout(Duration.ofMillis(25))
                    .requestTimeoutSchedulerFactory { scheduler }
                    .addResetHook { _, _, request ->
                        resetRequests += request
                        ResetOutcome.RETIRE
                    }.build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val workerId = lease.scope().workerId()
            val requestId = lease.scope().requestId()

            scheduler.triggerTimeout(workerId)
            lease.close()

            assertTrue(resetRequests.any { it.reason() == ResetRequest.Reason.TIMEOUT })
            assertTrue(resetRequests.any { it.requestId() == requestId })
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun immediateTimeoutStillRetiresWorker() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val scheduler = EagerRequestTimeoutScheduler()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .requestTimeout(Duration.ofMillis(100))
                    .diagnosticsListener(diagnostics)
                    .requestTimeoutSchedulerFactory { scheduler }
                    .build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val initialId = (lease.session() as FakeInteractiveSession).id
            val requestId = lease.scope().requestId()

            assertTrue(diagnostics.leaseTimeouts.contains(requestId))
            assertTrue(scheduler.completed(requestId))

            lease.close()

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(initialId, replacementId)
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun acquireBlocksUntilLeaseReleased() {
        val engine = FakeProcessEngine()
        val scheduler = ManualRequestTimeoutScheduler()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .maxRequestsPerWorker(3)
                    .requestTimeoutSchedulerFactory { scheduler }
                    .build(),
            )
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        try {
            val first = pool.acquire(Duration.ofSeconds(1))

            val future =
                executor.submit<WorkerLease> {
                    pool.acquire(Duration.ofSeconds(1))
                }

            Thread.sleep(50)
            assertTrue(!future.isDone)

            first.close()
            val second = future.get(1, TimeUnit.SECONDS)
            val firstSessionId = (first.session() as FakeInteractiveSession).id
            val secondSessionId = (second.session() as FakeInteractiveSession).id

            assertEquals(firstSessionId, secondSessionId)
            second.close()
        } finally {
            executor.shutdownNow()
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun warmupFailureUpdatesMetricsAndFailsAcquisition() {
        val engine = FakeProcessEngine()
        val attempts = AtomicInteger()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(0)
                    .maxSize(1)
                    .warmupAction {
                        attempts.incrementAndGet()
                        throw IllegalStateException("warmup boom")
                    }.build(),
            )

        try {
            assertFailsWith<ServiceUnavailableException> {
                pool.acquire(Duration.ofMillis(50))
            }
            assertEquals(1, attempts.get())
            val metrics = pool.snapshot()
            assertEquals(1, metrics.failedLaunchAttempts())
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun manualResetRequestsRetirement() {
        val engine = FakeProcessEngine()
        val resetRequests = mutableListOf<ResetRequest>()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .addResetHook { _, _, request ->
                        resetRequests += request
                        ResetOutcome.RETIRE
                    }.build(),
            )

        try {
            val lease = pool.acquire(Duration.ofSeconds(1))
            val initialId = (lease.session() as FakeInteractiveSession).id
            val manualRequest = ResetRequest.manual(lease.scope().requestId())

            lease.reset(manualRequest)
            lease.close()

            val replacement = pool.acquire(Duration.ofSeconds(1))
            val replacementId = (replacement.session() as FakeInteractiveSession).id
            replacement.close()

            assertNotEquals(initialId, replacementId)
            assertTrue(resetRequests.any { it.reason() == ResetRequest.Reason.MANUAL })
        } finally {
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    @Test
    fun queueRejectionNotifiesDiagnostics() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .maxSize(1)
                    .maxQueueDepth(0)
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        val lease = pool.acquire(Duration.ofSeconds(1))
        assertFailsWith<ServiceUnavailableException> {
            pool.acquire(Duration.ofSeconds(1))
        }

        assertEquals(1, diagnostics.queueRejections.size)
        val (pending, capacity) = diagnostics.queueRejections.single()
        assertEquals(0, capacity)
        assertEquals(0, pending)

        lease.close()
        pool.close()
        pool.drain(Duration.ofMillis(100))
    }

    @Test
    fun drainWaitsForActiveLeases() {
        val engine = FakeProcessEngine()
        val diagnostics = TrackingDiagnostics()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .diagnosticsListener(diagnostics)
                    .build(),
            )

        val lease = pool.acquire(Duration.ofSeconds(1))
        val closer =
            Thread {
                Thread.sleep(75)
                lease.close()
            }

        pool.close()
        closer.start()
        val drained = pool.drain(Duration.ofSeconds(1))
        closer.join()

        assertTrue(drained)
        assertTrue(diagnostics.draining)
        assertTrue(diagnostics.terminated)
    }

    @Test
    fun drainClosesRequestTimeoutScheduler() {
        val engine = FakeProcessEngine()
        val scheduler = ClosingRequestTimeoutScheduler()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .requestTimeout(Duration.ofSeconds(1))
                    .requestTimeoutSchedulerFactory { scheduler }
                    .build(),
            )

        pool.close()
        val drained = pool.drain(Duration.ofSeconds(1))

        assertTrue(drained, "pool must drain immediately when idle")
        assertTrue(scheduler.closed.get(), "drain must close the request timeout scheduler")
    }

    @Test
    fun acquireAsyncDelegatesToScheduler() {
        val engine = FakeProcessEngine()
        val pool =
            ProcessPool.create(
                engine,
                ProcessPoolConfig
                    .builder(COMMAND)
                    .minSize(1)
                    .maxSize(1)
                    .build(),
            )
        val scheduler = ImmediateScheduler()

        try {
            val future = pool.acquireAsync(scheduler, Duration.ofSeconds(1))
            val lease = future.toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertEquals(1, scheduler.invocations.get())
            lease.close()
        } finally {
            scheduler.close()
            pool.close()
            pool.drain(Duration.ofMillis(100))
        }
    }

    private class FailsOnceProcessEngine(
        private val failAttempt: Int,
    ) : ProcessEngine {
        private val attempts = AtomicInteger()
        private val successes = AtomicInteger()
        private val failed = AtomicBoolean(false)

        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult = throw UnsupportedOperationException("Not required for tests")

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession {
            val attempt = attempts.incrementAndGet()
            if (!failed.get() && attempt == failAttempt) {
                failed.set(true)
                throw RuntimeException("Simulated launch failure on attempt $attempt")
            }
            val id = successes.incrementAndGet()
            return FakeInteractiveSession(id)
        }

        fun createdCount(): Int = successes.get()
    }

    private class AlwaysFailProcessEngine : ProcessEngine {
        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult = throw UnsupportedOperationException("Not required for tests")

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession = throw RuntimeException("Simulated launch failure")
    }

    private class FakeProcessEngine : ProcessEngine {
        private val counter = AtomicInteger()

        override fun run(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): ProcessResult = throw UnsupportedOperationException("Not required for tests")

        override fun startSession(
            spec: CommandDefinition,
            options: ExecutionOptions,
        ): InteractiveSession {
            val id = counter.incrementAndGet()
            return FakeInteractiveSession(id)
        }

        fun createdCount(): Int = counter.get()
    }

    private class FakeInteractiveSession(
        val id: Int,
    ) : InteractiveSession {
        private val exit = CompletableFuture<Int>()
        private val closed = AtomicBoolean(false)
        private val signals = AtomicInteger(0)

        override fun stdin(): OutputStream = OutputStream.nullOutputStream()

        override fun stdout(): InputStream = InputStream.nullInputStream()

        override fun stderr(): InputStream = InputStream.nullInputStream()

        override fun onExit(): CompletableFuture<Int> = exit

        override fun closeStdin() {}

        override fun sendSignal(signal: ShutdownSignal) {
            signals.incrementAndGet()
        }

        override fun resizePty(
            columns: Int,
            rows: Int,
        ) {}

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                exit.complete(0)
            }
        }

        fun isClosed(): Boolean = closed.get()

        fun signalCount(): Int = signals.get()
    }

    private companion object {
        private val COMMAND = CommandDefinition.of(listOf("fake"))
    }

    private fun assertNoConsecutiveDuplicateMetrics(snapshots: List<PoolMetrics>) {
        snapshots
            .zipWithNext()
            .forEachIndexed { index, pair ->
                val (first, second) = pair
                assertNotEquals(
                    first,
                    second,
                    "Metrics update at index ${index + 1} matched the previous snapshot",
                )
            }
    }

    private class TrackingDiagnostics : PoolDiagnosticsListener {
        val workerCreatedIds = mutableListOf<Int>()
        val retiredWorkers = mutableListOf<Pair<Int, WorkerRetirementReason>>()
        val leaseAcquiredWorkers = mutableListOf<Int>()
        val leaseReleasedWorkers = mutableListOf<Int>()
        val queueRejections = mutableListOf<Pair<Int, Int>>()
        val leaseTimeouts = mutableListOf<UUID>()
        val metricsSnapshots = mutableListOf<PoolMetrics>()

        @Volatile var draining: Boolean = false

        @Volatile var terminated: Boolean = false

        override fun workerCreated(workerId: Int) {
            workerCreatedIds += workerId
        }

        override fun workerRetired(
            workerId: Int,
            reason: WorkerRetirementReason,
        ) {
            retiredWorkers += workerId to reason
        }

        override fun leaseAcquired(workerId: Int) {
            leaseAcquiredWorkers += workerId
        }

        override fun leaseReleased(workerId: Int) {
            leaseReleasedWorkers += workerId
        }

        override fun queueRejected(
            pendingWaiters: Int,
            queueCapacity: Int,
        ) {
            queueRejections += pendingWaiters to queueCapacity
        }

        override fun poolDraining() {
            draining = true
        }

        override fun poolTerminated() {
            terminated = true
        }

        override fun leaseTimedOut(
            workerId: Int,
            requestId: UUID,
        ) {
            leaseTimeouts += requestId
        }

        override fun metricsUpdated(metrics: PoolMetrics) {
            metricsSnapshots += metrics
        }
    }

    private class ImmediateScheduler : ClientScheduler {
        val invocations = AtomicInteger()

        override fun <T> submit(task: java.util.concurrent.Callable<T>): CompletableFuture<T> {
            invocations.incrementAndGet()
            return try {
                CompletableFuture.completedFuture(task.call())
            } catch (ex: Throwable) {
                val future = CompletableFuture<T>()
                future.completeExceptionally(ex)
                future
            }
        }

        override fun close() {
            // no-op for tests
        }
    }

    private class MutableClock(
        private var current: Instant,
        private val zoneId: ZoneId = ZoneId.systemDefault(),
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock =
            if (zone == zoneId) {
                this
            } else {
                MutableClock(current, zone)
            }

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private class ManualRequestTimeoutScheduler : RequestTimeoutScheduler {
        private val callbacks = ConcurrentHashMap<Int, TimeoutEntry>()
        private val firedRequests = ConcurrentHashMap.newKeySet<UUID>()

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
            firedRequests += requestId
            return true
        }

        override fun close() {
            callbacks.clear()
        }

        fun triggerTimeout(workerId: Int) {
            callbacks[workerId]?.onTimeout?.run()
        }

        fun triggered(requestId: UUID): Boolean = firedRequests.contains(requestId)

        private data class TimeoutEntry(
            val requestId: UUID,
            val onTimeout: Runnable,
        )
    }

    private class ClosingRequestTimeoutScheduler : RequestTimeoutScheduler {
        val closed = AtomicBoolean(false)

        override fun schedule(
            workerId: Int,
            requestId: UUID,
            timeout: Duration,
            onTimeout: Runnable,
        ) {}

        override fun cancel(workerId: Int) {}

        override fun complete(
            workerId: Int,
            requestId: UUID,
        ): Boolean = false

        override fun close() {
            closed.set(true)
        }
    }

    private class EagerRequestTimeoutScheduler : RequestTimeoutScheduler {
        private val callbacks = ConcurrentHashMap<Int, TimeoutEntry>()
        private val completed = ConcurrentHashMap.newKeySet<UUID>()

        override fun schedule(
            workerId: Int,
            requestId: UUID,
            timeout: Duration,
            onTimeout: Runnable,
        ) {
            callbacks[workerId] = TimeoutEntry(requestId, onTimeout)
            onTimeout.run()
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
            completed += requestId
            return true
        }

        override fun close() {
            callbacks.clear()
        }

        fun completed(requestId: UUID): Boolean = completed.contains(requestId)

        private data class TimeoutEntry(
            val requestId: UUID,
            val onTimeout: Runnable,
        )
    }
}
