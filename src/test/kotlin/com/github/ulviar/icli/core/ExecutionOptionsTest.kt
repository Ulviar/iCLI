package com.github.ulviar.icli.core

import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsListener
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionOptionsTest {
    @Test
    fun `builder provides sensible defaults`() {
        val options = ExecutionOptions.builder().build()

        val stdout = options.stdoutPolicy() as OutputCapture.Bounded
        val stderr = options.stderrPolicy() as OutputCapture.Bounded
        val plan = options.shutdownPlan()

        assertEquals(64 * 1024, stdout.maxBytes)
        assertEquals(StandardCharsets.UTF_8, stdout.charset)
        assertEquals(32 * 1024, stderr.maxBytes)
        assertFalse(options.mergeErrorIntoOutput())
        assertTrue(options.destroyProcessTree())

        assertEquals(Duration.ofSeconds(60), plan.softTimeout())
        assertEquals(Duration.ofSeconds(5), plan.gracePeriod())
        assertEquals(ShutdownSignal.INTERRUPT, plan.signal())
    }

    @Test
    fun `custom policies and flags are applied`() {
        val stdoutPolicy = OutputCapture.streaming()
        val stderrPolicy = OutputCapture.discard()
        val shutdown =
            ShutdownPlan(Duration.ofSeconds(10), Duration.ofSeconds(1), ShutdownSignal.TERMINATE)
        val diagnostics = DiagnosticsListener { }

        val options =
            ExecutionOptions
                .builder()
                .stdoutPolicy(stdoutPolicy)
                .stderrPolicy(stderrPolicy)
                .mergeErrorIntoOutput(true)
                .shutdownPlan(shutdown)
                .destroyProcessTree(false)
                .diagnosticsListener(diagnostics)
                .build()

        assertTrue(options.stdoutPolicy() is OutputCapture.Streaming)
        assertTrue(options.stderrPolicy() is OutputCapture.Discard)
        assertTrue(options.mergeErrorIntoOutput())
        assertFalse(options.destroyProcessTree())
        assertEquals(shutdown, options.shutdownPlan())
        assertEquals(diagnostics, options.diagnosticsListener())
    }

    @Test
    fun `output capture factory helpers`() {
        val bounded = OutputCapture.bounded(1024)
        val streaming = OutputCapture.streaming()
        val discard = OutputCapture.discard()

        assertEquals(1024, (bounded as OutputCapture.Bounded).maxBytes)
        assertEquals(StandardCharsets.UTF_8, bounded.charset)

        assertEquals(StandardCharsets.UTF_8, (streaming as OutputCapture.Streaming).charset)
        assertTrue(streaming.isStreaming())

        assertTrue(discard is OutputCapture.Discard)
        assertFalse(discard.isStreaming())
    }

    @Test
    fun `diagnostics listener defaults to no-op`() {
        val options = ExecutionOptions.builder().build()

        assertEquals(DiagnosticsListener.noOp(), options.diagnosticsListener())
    }
}
