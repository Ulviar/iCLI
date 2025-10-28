package com.github.ulviar.icli.core.runtime.shutdown

import com.github.ulviar.icli.core.ShutdownPlan
import com.github.ulviar.icli.core.ShutdownSignal
import com.github.ulviar.icli.core.runtime.ProcessShutdownException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShutdownExecutorTest {
    @Test
    fun `sends signal and escalates to force kill`() {
        val process = NeverEndingProcess()
        val terminator = RecordingProcessTerminator()
        val executor = ShutdownExecutor(terminator)
        val plan = ShutdownPlan(Duration.ofMillis(10), Duration.ofMillis(5), ShutdownSignal.INTERRUPT)

        executor.awaitCompletion(process, plan, true)

        assertTrue(terminator.destroyCalls.any { it.force.not() })
        assertTrue(terminator.destroyCalls.any { it.force })
    }

    @Test
    fun `does nothing when process exits within soft timeout`() {
        val process = ImmediateProcess()
        val terminator = RecordingProcessTerminator()
        val executor = ShutdownExecutor(terminator)
        val plan = ShutdownPlan(Duration.ofSeconds(1), Duration.ofMillis(100), ShutdownSignal.TERMINATE)

        executor.awaitCompletion(process, plan, false)

        assertTrue(terminator.destroyCalls.isEmpty())
    }

    @Test
    fun `propagates destroyTree flag`() {
        val process = NeverEndingProcess()
        val terminator = RecordingProcessTerminator()
        val executor = ShutdownExecutor(terminator)
        val plan = ShutdownPlan(Duration.ofMillis(5), Duration.ofMillis(5), ShutdownSignal.KILL)

        executor.awaitCompletion(process, plan, false)

        assertTrue(terminator.destroyCalls.all { it.destroyTree == false })
    }

    @Test
    fun `throws shutdown exception when interruption occurs`() {
        val process = InterruptingProcess()
        val terminator = RecordingProcessTerminator()
        val executor = ShutdownExecutor(terminator)
        val plan = ShutdownPlan(Duration.ofMillis(5), Duration.ofMillis(5), ShutdownSignal.TERMINATE)

        val ex =
            assertFailsWith<ProcessShutdownException> {
                executor.awaitCompletion(process, plan, true)
            }

        assertTrue(ex.cause is InterruptedException)
        assertTrue(terminator.destroyCalls.any { it.force })
    }

    private class NeverEndingProcess : Process() {
        override fun destroy() {}

        override fun destroyForcibly(): Process = this

        override fun exitValue(): Int = 0

        override fun getErrorStream(): InputStream = throw UnsupportedOperationException()

        override fun getInputStream(): InputStream = throw UnsupportedOperationException()

        override fun getOutputStream(): OutputStream = throw UnsupportedOperationException()

        override fun waitFor(): Int = 0

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = false
    }

    private class ImmediateProcess : Process() {
        override fun destroy() {}

        override fun destroyForcibly(): Process = this

        override fun exitValue(): Int = 0

        override fun getErrorStream(): InputStream = throw UnsupportedOperationException()

        override fun getInputStream(): InputStream = throw UnsupportedOperationException()

        override fun getOutputStream(): OutputStream = throw UnsupportedOperationException()

        override fun waitFor(): Int = 0

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = true
    }

    private class InterruptingProcess : Process() {
        override fun destroy() {}

        override fun destroyForcibly(): Process = this

        override fun exitValue(): Int = 0

        override fun getErrorStream(): InputStream = throw UnsupportedOperationException()

        override fun getInputStream(): InputStream = throw UnsupportedOperationException()

        override fun getOutputStream(): OutputStream = throw UnsupportedOperationException()

        override fun waitFor(): Int = 0

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = throw InterruptedException("simulated interruption")
    }

    private class RecordingProcessTerminator : ProcessTerminator {
        data class Call(
            val destroyTree: Boolean,
            val force: Boolean,
        )

        val destroyCalls = mutableListOf<Call>()

        override fun terminate(
            process: Process,
            destroyTree: Boolean,
            force: Boolean,
        ) {
            destroyCalls += Call(destroyTree, force)
        }
    }
}
