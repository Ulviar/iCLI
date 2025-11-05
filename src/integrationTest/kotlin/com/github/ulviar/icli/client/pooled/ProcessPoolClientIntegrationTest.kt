package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.ClientSchedulers
import com.github.ulviar.icli.client.CommandResult
import com.github.ulviar.icli.client.LineSessionException
import com.github.ulviar.icli.client.ListenOnlySessionClient
import com.github.ulviar.icli.client.ResponseDecoder
import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.PoolDiagnosticsListener
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.pool.api.ServiceProcessingException
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine
import com.github.ulviar.icli.testing.ProcessFixtureCommand
import com.github.ulviar.icli.testing.TestProcessCommand
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessPoolClientIntegrationTest {
    @Test
    fun `service processor reuses the same worker for sequential requests`() {
        withClient(
            configure = {
                minSize(1)
                maxSize(1)
            },
        ) { context ->
            val processor = context.client.serviceProcessor()

            assertLineResult("ping", processor.process("ping"))
            assertLineResult("pong", processor.process("pong"))

            assertEquals(listOf("ping", "pong"), context.listener.startedInputs())
            assertEquals(0, context.listener.failureCount())
            assertEquals(
                1,
                context.listener
                    .completedWorkerIds()
                    .toSet()
                    .size,
            )
        }
    }

    @Test
    fun `conversation holds one worker while stateless requests use another`() {
        withClient(
            configure = {
                minSize(2)
                maxSize(2)
            },
        ) { context ->
            context.client.openConversation().use { conversation ->
                assertLineResult("primary", conversation.line().process("primary"))

                val async = context.client.serviceProcessor().processAsync("secondary")
                val asyncResult = async.get(5, TimeUnit.SECONDS)
                assertLineResult("secondary", asyncResult)
            }

            val workerIds = context.listener.completedWorkerIds()
            val conversationIds = context.listener.conversationWorkerIds()

            assertTrue(workerIds.isNotEmpty(), "expected stateless worker events")
            assertTrue(conversationIds.isNotEmpty(), "expected conversation worker events")
            assertTrue((workerIds + conversationIds).toSet().size >= 2, "expected at least two distinct workers")
        }
    }

    @Test
    fun `retiring a conversation replaces the worker before next request`() {
        withClient(
            configure = {
                minSize(1)
                maxSize(1)
                maxRequestsPerWorker(1)
            },
        ) { context ->
            context.client.openConversation().use { conversation ->
                assertLineResult("first", conversation.line().process("first"))
                conversation.retire()
            }

            val result = context.client.serviceProcessor().process("second")
            assertLineResult("second", result)

            assertTrue(context.listener.resetCount() >= 1, "expected at least one reset event")
            val conversationWorker = context.listener.conversationWorkerIds().last()
            val statelessWorker = context.listener.completedWorkerIds().last()
            assertNotEquals(
                conversationWorker,
                statelessWorker,
                "retired worker should not handle subsequent requests",
            )
        }
    }

    @Test
    fun `fixture service processor recovers from hangs`() {
        val command =
            fixtureCommand(
                "--mode=line",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:12",
                "--seed=17",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(1)
                maxSize(1)
                requestTimeout(Duration.ofMillis(250))
            },
        ) { context ->
            val processor = context.client.serviceProcessor()
            assertFixtureResult("alpha", processor.process("""{"label":"alpha","runtimeMs":10}"""))
            assertFixtureResult("beta", processor.process("""{"label":"beta","runtimeMs":12}"""))

            val hanging = processor.process("HANG")
            assertTrue(!hanging.success, "expected failure for hang scenario")
            assertTrue(hanging.error is LineSessionException)

            assertFixtureResult("gamma", processor.process("""{"label":"gamma","runtimeMs":8}"""))
            assertTrue(context.listener.failureCount() >= 1)
            assertTrue(
                context.listener.distinctWorkerIds() >= 2,
                "expected hang to trigger worker replacement",
            )
        }
    }

    @Test
    fun `fixture service processor uses multiple workers for concurrent requests`() {
        val command =
            fixtureCommand(
                "--mode=line",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:10",
                "--seed=29",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(2)
                maxSize(2)
                requestTimeout(Duration.ofSeconds(2))
            },
        ) { context ->
            val processor = context.client.serviceProcessor()
            val slow = processor.processAsync("""{"label":"slow","runtimeMs":150}""")
            val fast = processor.processAsync("""{"label":"fast","runtimeMs":10}""")

            val fastResult = fast.get(5, TimeUnit.SECONDS)
            val slowResult = slow.get(5, TimeUnit.SECONDS)

            assertFixtureResult("fast", fastResult)
            assertFixtureResult("slow", slowResult)
            assertTrue(
                context.listener.distinctWorkerIds() >= 2,
                "expected two workers to handle concurrent requests",
            )
        }
    }

    @Test
    fun `fixture conversation applies config overrides while stateless requests run`() {
        val command =
            fixtureCommand(
                "--mode=line",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:16",
                "--seed=41",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(2)
                maxSize(2)
                requestTimeout(Duration.ofSeconds(2))
            },
        ) { context ->
            context.client.openConversation().use { conversation ->
                val config =
                    conversation
                        .line()
                        .process("""CONFIG {"runtimeMinMs":0,"runtimeMaxMs":0,"payload":{"type":"text","size":8}}""")
                assertEquals("CONFIG-OK", config.value)

                val resetAck = conversation.line().process("RESET")
                assertEquals("RESET-OK", resetAck.value)

                conversation.reset()
                assertEquals(1, context.listener.resetCount())

                val convResult = conversation.line().process("""{"label":"conversation","runtimeMs":5}""")
                assertFixtureResult("conversation", convResult)

                val stateless = context.client.serviceProcessor().processAsync("""{"label":"stateless","runtimeMs":5}""")
                assertFixtureResult("stateless", stateless.get(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `fixture streaming publishes chunks through pooled listen-only client`() {
        val command =
            fixtureCommand(
                "--mode=stream",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:6",
                "--streaming=burst",
                "--stream-max-chunks=4",
                "--seed=57",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(1)
                maxSize(1)
                requestTimeout(Duration.ofSeconds(2))
            },
        ) { context ->
            context.client.openConversation().use { conversation ->
                val listenOnly = ListenOnlySessionClient.share(conversation.interactive())
                val subscriber = RecordingSubscriber()
                try {
                    listenOnly.stdoutPublisher().subscribe(subscriber)
                    subscriber.request(Long.MAX_VALUE)

                    subscriber.awaitCondition { subscriber.content().contains("CHUNK") }

                    listenOnly.interactive().sendLine("STOP")
                    subscriber.awaitCondition { subscriber.content().contains("STREAM-COMPLETE") }
                } finally {
                    listenOnly.close()
                }
            }
        }
    }

    @Test
    fun `fixture streaming pauses and resumes while stateless requests run`() {
        val command =
            fixtureCommand(
                "--mode=stream",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:5",
                "--streaming=chunked",
                "--stream-max-chunks=12",
                "--seed=73",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(2)
                maxSize(2)
                requestTimeout(Duration.ofSeconds(2))
            },
        ) { context ->
            context.client.openConversation().use { conversation ->
                val listenOnly = ListenOnlySessionClient.share(conversation.interactive())
                val subscriber = RecordingSubscriber()
                listenOnly.stdoutPublisher().subscribe(subscriber)
                subscriber.request(Long.MAX_VALUE)

                subscriber.awaitCondition { chunkCount(subscriber.content()) >= 1 }
                val initialChunks = chunkCount(subscriber.content())

                listenOnly.interactive().sendLine("PAUSE")

                val pauseDeadline = System.nanoTime() + Duration.ofMillis(150).toNanos()
                while (System.nanoTime() < pauseDeadline) {
                    assertEquals(initialChunks, chunkCount(subscriber.content()), "chunks should pause while paused")
                    Thread.sleep(5)
                }

                listenOnly.interactive().sendLine("RESUME")
                subscriber.awaitCondition { chunkCount(subscriber.content()) > initialChunks }

                listenOnly.interactive().sendLine("STOP")
                subscriber.awaitCondition { subscriber.content().contains("STREAM-COMPLETE") }
                listenOnly.close()
            }
        }
    }

    @Test
    fun `fixture hang timeout emits service processing diagnostics`() {
        val diagnostics = RecordingDiagnosticsListener()
        val command =
            fixtureCommand(
                "--mode=line",
                "--runtime-min-ms=0",
                "--runtime-max-ms=0",
                "--payload=text:8",
                "--seed=83",
            )

        withClient(
            commandDefinition = command,
            decoder = fixtureResponseDecoder(),
            configure = {
                minSize(1)
                maxSize(1)
                requestTimeout(Duration.ofMillis(100))
                diagnosticsListener(diagnostics)
            },
        ) { context ->
            val processor = context.client.serviceProcessor()
            val result = processor.process("HANG")
            assertFalse(result.success, "hang request should fail")
            assertIs<LineSessionException>(result.error)
            assertTrue(context.listener.failureCount() >= 1)
            assertTrue(
                diagnostics.failureCauses().any { it is ServiceProcessingException },
                "expected ServiceProcessingException diagnostics, saw ${diagnostics.failureCauses()}",
            )
            assertEquals(Duration.ofMillis(100), context.config.requestTimeout(), "requestTimeout override not applied")
        }
    }

    private fun withClient(
        commandDefinition: CommandDefinition = testCommand(),
        decoder: ResponseDecoder = ResponseDecoder.lineDelimited(),
        configure: ProcessPoolConfig.Builder.() -> Unit = {},
        block: (ClientContext) -> Unit,
    ) {
        val scheduler = ClientSchedulers.virtualThreads()
        val listener = ThreadSafeListener()
        val engine = StandardProcessEngine()
        val config =
            poolConfigBuilder(commandDefinition)
                .apply(configure)
                .build()

        val client =
            ProcessPoolClient.create(
                engine,
                config,
                scheduler,
                decoder,
                listener,
            )

        try {
            client.use { current ->
                block(ClientContext(current, listener, config))
            }
        } finally {
            scheduler.close()
        }
    }

    private fun poolConfigBuilder(command: CommandDefinition): ProcessPoolConfig.Builder =
        ProcessPoolConfig
            .builder(command)
            .workerOptions(workerOptions())
            .minSize(1)
            .maxSize(1)
            .leaseTimeout(Duration.ofSeconds(2))
            .requestTimeout(Duration.ofSeconds(5))

    private fun workerOptions(): ExecutionOptions =
        ExecutionOptions
            .builder()
            .idleTimeout(Duration.ZERO)
            .build()

    private fun testCommand(): CommandDefinition =
        CommandDefinition
            .builder()
            .command(TestProcessCommand.command("--echo-stdin"))
            .terminalPreference(TerminalPreference.DISABLED)
            .build()

    private fun fixtureCommand(vararg args: String): CommandDefinition =
        CommandDefinition
            .builder()
            .command(ProcessFixtureCommand.command(*args))
            .terminalPreference(TerminalPreference.DISABLED)
            .build()

    private fun fixtureResponseDecoder(): ResponseDecoder =
        ResponseDecoder { stdout, charset ->
            val buffer = ByteArrayOutputStream()
            var decoded: String? = null
            while (decoded == null) {
                val value = stdout.read()
                if (value == -1) {
                    throw IOException("Fixture terminated before emitting a response")
                }
                if (value == '\n'.code) {
                    var line = String(buffer.toByteArray(), charset)
                    buffer.reset()
                    if (line.endsWith("\r")) {
                        line = line.dropLast(1)
                    }
                    if (line.isBlank() || line.startsWith("{") || line.startsWith("READY")) {
                        continue
                    }
                    decoded = line
                } else {
                    buffer.write(value)
                }
            }
            decoded!!
        }

    private fun assertFixtureResult(
        expectedLabel: String,
        result: CommandResult<String>,
    ) {
        assertTrue(result.success, "expected success but observed ${result.error}")
        val parsed = parseFixtureLine(result.value!!)
        assertEquals(expectedLabel, parsed.label, "unexpected payload: ${result.value}")
    }

    private fun parseFixtureLine(line: String): FixtureLineResponse {
        val trimmed = line.trim()
        require(trimmed.startsWith("RESULT")) { "Unexpected fixture output: $line" }
        val parts = trimmed.split(' ', limit = 3)
        require(parts.size == 3) { "Fixture result must have three parts: $line" }
        val requestId = parts[1].toLong()
        val payload = parts[2]
        val label = payload.substringAfterLast('-')
        return FixtureLineResponse(requestId, payload, label)
    }

    private data class FixtureLineResponse(
        val requestId: Long,
        val payload: String,
        val label: String,
    )

    private class RecordingSubscriber : Flow.Subscriber<ByteBuffer> {
        private lateinit var subscription: Flow.Subscription
        private val output = StringBuilder()

        @Volatile private var error: Throwable? = null

        override fun onSubscribe(subscription: Flow.Subscription) {
            this.subscription = subscription
        }

        override fun onNext(item: ByteBuffer) {
            val bytes = ByteArray(item.remaining())
            item.get(bytes)
            output.append(String(bytes, StandardCharsets.UTF_8))
        }

        override fun onError(throwable: Throwable) {
            error = throwable
        }

        override fun onComplete() {}

        fun request(n: Long) {
            subscription.request(n)
        }

        fun content(): String = output.toString()

        fun awaitCondition(
            timeout: Duration = Duration.ofSeconds(2),
            predicate: () -> Boolean,
        ) {
            val deadline = System.nanoTime() + timeout.toNanos()
            while (System.nanoTime() < deadline) {
                if (predicate()) {
                    return
                }
                if (error != null) {
                    throw IllegalStateException("Subscriber failed", error)
                }
                Thread.sleep(10)
            }
            throw IllegalStateException("Timed out after $timeout waiting for condition; observed $output")
        }
    }

    private fun chunkCount(transcript: String): Int =
        transcript
            .lineSequence()
            .count { it.startsWith("CHUNK") }

    private class RecordingDiagnosticsListener : PoolDiagnosticsListener {
        private val failures = ConcurrentLinkedQueue<Throwable>()

        override fun workerFailed(
            workerId: Int,
            failure: Throwable,
        ) {
            failures += failure
        }

        fun failureCauses(): List<Throwable> = failures.toList()
    }

    private fun assertLineResult(
        expected: String,
        result: CommandResult<String>,
    ) {
        assertTrue(result.success, "expected success but saw ${result.error}")
        assertEquals(expected, result.value)
    }

    private data class ClientContext(
        val client: ProcessPoolClient,
        val listener: ThreadSafeListener,
        val config: ProcessPoolConfig,
    )

    private class ThreadSafeListener : ServiceProcessorListener {
        private val started = ConcurrentLinkedQueue<String>()
        private val completedWorkers = ConcurrentLinkedQueue<Int>()
        private val failures = ConcurrentLinkedQueue<Throwable>()
        private val failedWorkers = ConcurrentLinkedQueue<Int>()
        private val conversationWorkers = ConcurrentLinkedQueue<Int>()
        private val resets = AtomicInteger()

        override fun requestStarted(
            scope: LeaseScope,
            input: String,
        ) {
            started += input
        }

        override fun requestCompleted(
            scope: LeaseScope,
            result: CommandResult<String>,
        ) {
            completedWorkers += scope.workerId()
        }

        override fun requestFailed(
            scope: LeaseScope,
            error: Throwable,
        ) {
            failures += error
            failedWorkers += scope.workerId()
        }

        override fun conversationOpened(scope: LeaseScope) {
            conversationWorkers += scope.workerId()
        }

        override fun conversationReset(scope: LeaseScope) {
            resets.incrementAndGet()
        }

        fun startedInputs(): List<String> = started.toList()

        fun completedWorkerIds(): List<Int> = completedWorkers.toList()

        fun conversationWorkerIds(): List<Int> = conversationWorkers.toList()

        fun resetCount(): Int = resets.get()

        fun failureCount(): Int = failures.size

        fun distinctWorkerIds(): Int =
            (completedWorkers.toList() + conversationWorkers.toList() + failedWorkers.toList())
                .toSet()
                .size
    }
}
