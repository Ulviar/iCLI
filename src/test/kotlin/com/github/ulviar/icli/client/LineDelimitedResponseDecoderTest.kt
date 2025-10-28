package com.github.ulviar.icli.client

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LineDelimitedResponseDecoderTest {
    @Test
    fun `reads up to newline using default delimiter`() {
        val decoder = LineDelimitedResponseDecoder()
        val stream = ByteArrayInputStream("hello\nnext".toByteArray(StandardCharsets.UTF_8))

        val result = decoder.read(stream, StandardCharsets.UTF_8)

        assertEquals("hello", result)
        assertEquals("next".length, stream.available(), "decoder should leave remaining bytes unread")
    }

    @Test
    fun `supports custom delimiter`() {
        val decoder = LineDelimitedResponseDecoder(';'.code)
        val stream = ByteArrayInputStream("value;other".toByteArray(StandardCharsets.UTF_8))

        val result = decoder.read(stream, StandardCharsets.UTF_8)

        assertEquals("value", result)
    }

    @Test
    fun `strips carriage return when using default newline delimiter`() {
        val decoder = LineDelimitedResponseDecoder()
        val stream = ByteArrayInputStream("windows\r\nnext".toByteArray(StandardCharsets.UTF_8))

        val result = decoder.read(stream, StandardCharsets.UTF_8)

        assertEquals("windows", result)
        assertEquals("next", stream.readBytes().toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `throws when delimiter never encountered`() {
        val decoder = LineDelimitedResponseDecoder()
        val stream = ByteArrayInputStream("unterminated".toByteArray(StandardCharsets.UTF_8))

        assertFailsWith<IOException> {
            decoder.read(stream, StandardCharsets.UTF_8)
        }
    }
}
