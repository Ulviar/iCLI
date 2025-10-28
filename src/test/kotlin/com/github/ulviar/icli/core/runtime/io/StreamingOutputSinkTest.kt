package com.github.ulviar.icli.core.runtime.io

import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsEvent
import com.github.ulviar.icli.core.runtime.diagnostics.DiagnosticsListener
import com.github.ulviar.icli.core.runtime.diagnostics.StreamType
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingOutputSinkTest {
    @Test
    fun `emits chunk events without retaining content`() {
        val recorder = RecordingDiagnostics()
        val sink = StreamingOutputSink(StandardCharsets.UTF_8, StreamType.STDOUT, recorder)

        sink.append("chunk".toByteArray(), 0, 5)

        assertTrue(recorder.events.single() is DiagnosticsEvent.OutputChunk)
        val event = recorder.events.single() as DiagnosticsEvent.OutputChunk
        assertEquals("chunk", event.text(StandardCharsets.UTF_8))
        assertEquals("", sink.content())
    }

    @Test
    fun `payload accessor returns defensive copy`() {
        val recorder = RecordingDiagnostics()
        val sink = StreamingOutputSink(StandardCharsets.UTF_8, StreamType.STDOUT, recorder)

        sink.append("copy".toByteArray(), 0, 4)

        val event = recorder.events.single() as DiagnosticsEvent.OutputChunk
        val first = event.payload()
        first[0] = 'X'.code.toByte()
        val second = event.payload()
        assertEquals("copy", second.toString(StandardCharsets.UTF_8))
    }

    private class RecordingDiagnostics : DiagnosticsListener {
        val events: MutableList<DiagnosticsEvent> = mutableListOf()

        override fun onEvent(event: DiagnosticsEvent) {
            events += event
        }
    }
}
