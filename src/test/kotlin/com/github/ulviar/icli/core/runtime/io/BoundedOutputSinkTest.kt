package com.github.ulviar.icli.core.runtime.io

import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsEvent
import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsListener
import com.github.ulviar.icli.core.runtime.diagnostics.StreamType
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundedOutputSinkTest {
    @Test
    fun `stores up to configured bytes`() {
        val recorder = RecordingDiagnostics()
        val sink = BoundedOutputSink(4, StandardCharsets.UTF_8, StreamType.STDOUT, recorder)

        sink.append("abcdef".toByteArray(), 0, 6)

        assertEquals("abcd", sink.content())
        assertTrue(recorder.events.isNotEmpty())
        val event = recorder.events.first() as DiagnosticsEvent.OutputTruncated
        assertEquals("ef", event.preview(StandardCharsets.UTF_8))
        assertEquals(4, event.retainedBytes())
        assertEquals(2, event.discardedBytes())
    }

    @Test
    fun `rejects non-positive limits`() {
        assertFailsWith<IllegalArgumentException> {
            BoundedOutputSink(0, StandardCharsets.UTF_8, StreamType.STDERR, DiagnosticsListener.noOp())
        }
    }

    @Test
    fun `appending in chunks respects remaining capacity`() {
        val sink =
            BoundedOutputSink(
                5,
                StandardCharsets.UTF_8,
                StreamType.STDOUT,
                DiagnosticsListener.noOp(),
            )

        sink.append("abc".toByteArray(), 0, 3)
        sink.append("defg".toByteArray(), 0, 4)

        assertEquals("abcde", sink.content())
    }

    @Test
    fun `append after buffer full is ignored`() {
        val recorder = RecordingDiagnostics()
        val sink = BoundedOutputSink(2, StandardCharsets.UTF_8, StreamType.STDERR, recorder)

        sink.append("ab".toByteArray(), 0, 2)
        sink.append("cd".toByteArray(), 0, 2)

        assertEquals("ab", sink.content())
        assertTrue(recorder.events.last() is DiagnosticsEvent.OutputTruncated)
    }

    @Test
    fun `truncated chunk accessor returns defensive copy`() {
        val recorder = RecordingDiagnostics()
        val sink = BoundedOutputSink(1, StandardCharsets.UTF_8, StreamType.STDERR, recorder)

        sink.append("ab".toByteArray(), 0, 2)

        val event = recorder.events.single() as DiagnosticsEvent.OutputTruncated
        val first = event.truncatedChunk()
        first[0] = 'X'.code.toByte()
        val second = event.truncatedChunk()
        assertEquals("b", second.toString(StandardCharsets.UTF_8))
    }

    private class RecordingDiagnostics : DiagnosticsListener {
        val events: MutableList<DiagnosticsEvent> = mutableListOf()

        override fun onEvent(event: DiagnosticsEvent) {
            events += event
        }
    }
}
