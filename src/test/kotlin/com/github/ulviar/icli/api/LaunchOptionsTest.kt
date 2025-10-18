package com.github.ulviar.icli.api

import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchOptionsTest {
    @Test
    fun `builder provides sensible defaults`() {
        val options = LaunchOptions.builder().build()

        val stdout = options.stdoutPolicy() as OutputCapturePolicy.Bounded
        val stderr = options.stderrPolicy() as OutputCapturePolicy.Bounded
        val termination = options.terminationPolicy()

        assertEquals(64 * 1024, stdout.maxBytes)
        assertEquals(StandardCharsets.UTF_8, stdout.charset)
        assertEquals(32 * 1024, stderr.maxBytes)
        assertEquals(false, options.mergeErrorIntoOutput())
        assertTrue(options.destroyProcessTree())

        assertEquals(Duration.ofSeconds(60), termination.softTimeout())
        assertEquals(Duration.ofSeconds(5), termination.gracePeriod())
        assertEquals(TerminationSignal.INTERRUPT, termination.signal())
    }

    @Test
    fun `custom policies and flags are applied`() {
        val stdoutPolicy = OutputCapturePolicy.streaming()
        val stderrPolicy = OutputCapturePolicy.discard()
        val termination =
            TerminationPolicy(Duration.ofSeconds(10), Duration.ofSeconds(1), TerminationSignal.TERMINATE)

        val options =
            LaunchOptions
                .builder()
                .stdoutPolicy(stdoutPolicy)
                .stderrPolicy(stderrPolicy)
                .mergeErrorIntoOutput(true)
                .terminationPolicy(termination)
                .destroyProcessTree(false)
                .build()

        assertTrue(options.stdoutPolicy() is OutputCapturePolicy.Streaming)
        assertTrue(options.stderrPolicy() is OutputCapturePolicy.Discard)
        assertTrue(options.mergeErrorIntoOutput())
        assertFalse(options.destroyProcessTree())
        assertEquals(termination, options.terminationPolicy())
    }

    @Test
    fun `output capture factory helpers`() {
        val bounded = OutputCapturePolicy.bounded(1024)
        val streaming = OutputCapturePolicy.streaming()
        val discard = OutputCapturePolicy.discard()

        assertEquals(1024, (bounded as OutputCapturePolicy.Bounded).maxBytes)
        assertEquals(StandardCharsets.UTF_8, bounded.charset)

        assertEquals(StandardCharsets.UTF_8, (streaming as OutputCapturePolicy.Streaming).charset)
        assertTrue(streaming.isStreaming())

        assertTrue(discard is OutputCapturePolicy.Discard)
        assertFalse(discard.isStreaming())
    }
}
