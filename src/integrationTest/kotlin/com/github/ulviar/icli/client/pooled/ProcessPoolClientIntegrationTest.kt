package com.github.ulviar.icli.client.pooled

import com.github.ulviar.icli.client.ClientSchedulers
import com.github.ulviar.icli.client.CommandResult
import com.github.ulviar.icli.client.ResponseDecoder
import com.github.ulviar.icli.engine.CommandDefinition
import com.github.ulviar.icli.engine.ExecutionOptions
import com.github.ulviar.icli.engine.TerminalPreference
import com.github.ulviar.icli.engine.pool.api.LeaseScope
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig
import com.github.ulviar.icli.engine.runtime.StandardProcessEngine
import com.github.ulviar.icli.testing.TestProcessCommand
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun withClient(
        configure: ProcessPoolConfig.Builder.() -> Unit = {},
        block: (ClientContext) -> Unit,
    ) {
        val scheduler = ClientSchedulers.virtualThreads()
        val listener = ThreadSafeListener()
        val engine = StandardProcessEngine()
        val config =
            poolConfigBuilder()
                .apply(configure)
                .build()

        val client =
            ProcessPoolClient.create(
                engine,
                config,
                scheduler,
                ResponseDecoder.lineDelimited(),
                listener,
            )

        try {
            client.use { current ->
                block(ClientContext(current, listener))
            }
        } finally {
            scheduler.close()
        }
    }

    private fun poolConfigBuilder(): ProcessPoolConfig.Builder =
        ProcessPoolConfig
            .builder(testCommand())
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
    )

    private class ThreadSafeListener : ServiceProcessorListener {
        private val started = ConcurrentLinkedQueue<String>()
        private val completedWorkers = ConcurrentLinkedQueue<Int>()
        private val failures = ConcurrentLinkedQueue<Throwable>()
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
    }
}
