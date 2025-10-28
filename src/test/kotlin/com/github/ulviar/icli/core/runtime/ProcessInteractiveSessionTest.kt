package com.github.ulviar.icli.core.runtime

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.SessionLifecycleObserver
import com.github.ulviar.icli.core.ShutdownPlan
import com.github.ulviar.icli.core.ShutdownSignal
import com.github.ulviar.icli.core.runtime.shutdown.ProcessTerminator
import com.github.ulviar.icli.core.runtime.shutdown.ShutdownExecutor
import com.github.ulviar.icli.core.runtime.terminal.TerminalController
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessInteractiveSessionTest {
    @Test
    fun `stdin writes reschedule idle timeout`() {
        val fixture = fixture()
        val before = fixture.scheduler.rescheduleCount.get()

        fixture.session.stdin().write("a".toByteArray(StandardCharsets.UTF_8))
        fixture.session.stdin().flush()

        assertTrue(
            fixture.scheduler.rescheduleCount.get() > before,
            "stdin writes should reschedule idle timeout",
        )
    }

    @Test
    fun `closeStdin is idempotent`() {
        val fixture = fixture()

        fixture.session.closeStdin()
        fixture.session.closeStdin()

        assertEquals(1, fixture.process.stdinCloseCount.get())
        assertTrue(fixture.process.stdinClosed.get())
    }

    @Test
    fun `close shuts down once and cancels idle scheduler`() {
        val fixture = fixture()
        fixture.process.completeExit(0)
        val initialCloseCount = fixture.scheduler.closeCount.get()
        val initialWaitCalls = fixture.process.waitForTimeoutCalls.get()

        fixture.session.close()
        fixture.session.close()

        assertEquals(initialWaitCalls + 1, fixture.process.waitForTimeoutCalls.get())
        assertEquals(initialCloseCount + 1, fixture.scheduler.closeCount.get())
        assertEquals(1, fixture.process.stdinCloseCount.get())
        assertTrue(fixture.terminator.calls.isEmpty(), "no termination expected when process exits promptly")
    }

    @Test
    fun `handleIdleTimeout notifies observer and closes session`() {
        val observed = AtomicBoolean(false)
        val fixture =
            fixture(
                idleTimeout = Duration.ofSeconds(5),
                observer = { command, timeout ->
                    observed.set(true)
                    assertEquals(listOf("cmd"), command.command())
                    assertEquals(Duration.ofSeconds(5), timeout)
                },
                autoCompleteExit = false,
            )
        val initialCloseCount = fixture.scheduler.closeCount.get()

        fixture.scheduler.triggerIdle()

        assertTrue(observed.get(), "observer should be notified")
        assertEquals(initialCloseCount + 1, fixture.scheduler.closeCount.get())
        assertEquals(2, fixture.process.waitForTimeoutCalls.get())
        assertEquals(
            listOf(
                RecordingProcessTerminator.Call(destroyTree = true, force = false),
                RecordingProcessTerminator.Call(destroyTree = true, force = true),
            ),
            fixture.terminator.calls,
        )
    }

    @Test
    fun `onExit future resolves to process exit code`() {
        val fixture = fixture(autoCompleteExit = false)

        fixture.process.completeExit(42)

        assertEquals(42, fixture.session.onExit().get(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `exit completion closes idle scheduler`() {
        val fixture = fixture(autoCompleteExit = false)

        fixture.process.completeExit(0)

        assertEquals(1, fixture.scheduler.closeCount.get())
    }

    @Test
    fun `sendSignal delegates to terminal controller`() {
        val controller = RecordingTerminalController()
        val fixture = fixture(controller = controller)

        fixture.session.sendSignal(ShutdownSignal.INTERRUPT)

        assertEquals(listOf(ShutdownSignal.INTERRUPT), controller.signals)
    }

    @Test
    fun `resizePty delegates to terminal controller`() {
        val controller = RecordingTerminalController()
        val fixture = fixture(controller = controller)

        fixture.session.resizePty(120, 40)

        assertEquals(listOf(120 to 40), controller.resizes)
    }

    @Test
    fun `stdin exposes idle-aware wrapper`() {
        val fixture = fixture()
        val before = fixture.scheduler.rescheduleCount.get()

        val stream = fixture.session.stdin()
        stream.write('x'.code)
        stream.flush()

        assertEquals("x", fixture.process.stdinBuffer.toString(StandardCharsets.UTF_8))
        assertTrue(
            fixture.scheduler.rescheduleCount.get() > before,
            "idle-aware stream should reschedule timeout",
        )
    }

    private fun fixture(
        idleTimeout: Duration = Duration.ZERO,
        observer: SessionLifecycleObserver = SessionLifecycleObserver.NO_OP,
        autoCompleteExit: Boolean = true,
        controller: RecordingTerminalController = RecordingTerminalController(),
    ): SessionFixture {
        val process = RecordingProcess()
        val scheduler = RecordingIdleScheduler()
        val terminator = RecordingProcessTerminator()
        val shutdownExecutor = ShutdownExecutor(terminator)

        val session =
            ProcessInteractiveSession(
                CommandDefinition.builder().command("cmd").build(),
                process,
                controller,
                shutdownExecutor,
                ShutdownPlan(Duration.ofMillis(10), Duration.ofMillis(10), ShutdownSignal.INTERRUPT),
                true,
                idleTimeout,
                observer,
            ) { timeout, callback ->
                scheduler.configure(timeout, callback)
                scheduler
            }

        if (autoCompleteExit) {
            process.completeExit(0)
        }

        return SessionFixture(session, process, scheduler, terminator, controller)
    }

    private data class SessionFixture(
        val session: ProcessInteractiveSession,
        val process: RecordingProcess,
        val scheduler: RecordingIdleScheduler,
        val terminator: RecordingProcessTerminator,
        val controller: RecordingTerminalController,
    )

    private class RecordingProcess : Process() {
        val stdinBuffer = ByteArrayOutputStream()
        val stdinCloseCount = AtomicInteger()
        val stdinClosed = AtomicBoolean(false)
        val waitForTimeoutCalls = AtomicInteger()
        val destroyCalls = AtomicInteger()
        val forceDestroyCalls = AtomicInteger()
        private val exitFuture = CompletableFuture<Process>()
        private val exitCode = AtomicInteger()

        fun completeExit(code: Int) {
            exitCode.set(code)
            exitFuture.complete(this)
        }

        override fun getOutputStream(): OutputStream =
            object : OutputStream() {
                override fun write(b: Int) {
                    stdinBuffer.write(b)
                }

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    stdinBuffer.write(b, off, len)
                }

                override fun flush() {}

                override fun close() {
                    stdinCloseCount.incrementAndGet()
                    stdinClosed.set(true)
                }
            }

        override fun getInputStream(): InputStream = EMPTY_STREAM

        override fun getErrorStream(): InputStream = EMPTY_STREAM

        override fun waitFor(): Int {
            exitFuture.join()
            return exitCode.get()
        }

        override fun waitFor(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean {
            waitForTimeoutCalls.incrementAndGet()
            return exitFuture.isDone
        }

        override fun onExit(): CompletableFuture<Process> = exitFuture

        override fun exitValue(): Int {
            if (!exitFuture.isDone) {
                throw IllegalThreadStateException("process still running")
            }
            return exitCode.get()
        }

        override fun destroy() {
            destroyCalls.incrementAndGet()
        }

        override fun destroyForcibly(): Process {
            forceDestroyCalls.incrementAndGet()
            destroy()
            return this
        }
    }

    private class RecordingIdleScheduler : IdleTimeoutScheduler {
        val rescheduleCount = AtomicInteger()
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()
        private var callback: Runnable? = null
        var timeout: Duration = Duration.ZERO
            private set

        fun configure(
            timeout: Duration,
            callback: Runnable,
        ) {
            this.timeout = timeout
            this.callback = callback
        }

        override fun reschedule() {
            rescheduleCount.incrementAndGet()
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
        }

        override fun close() {
            closeCount.incrementAndGet()
        }

        fun triggerIdle() {
            callback?.run()
        }
    }

    private class RecordingProcessTerminator : ProcessTerminator {
        data class Call(
            val destroyTree: Boolean,
            val force: Boolean,
        )

        val calls: MutableList<Call> = mutableListOf()

        override fun terminate(
            process: Process,
            destroyTree: Boolean,
            force: Boolean,
        ) {
            calls += Call(destroyTree, force)
        }
    }

    private class RecordingTerminalController : TerminalController {
        val signals: MutableList<ShutdownSignal> = mutableListOf()
        val resizes: MutableList<Pair<Int, Int>> = mutableListOf()

        override fun send(signal: ShutdownSignal) {
            signals += signal
        }

        override fun resize(
            columns: Int,
            rows: Int,
        ) {
            resizes += columns to rows
        }
    }

    private companion object {
        val EMPTY_STREAM: InputStream =
            object : InputStream() {
                override fun read(): Int = -1
            }
    }
}
