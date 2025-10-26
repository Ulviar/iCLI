package com.github.ulviar.icli.core.runtime.io

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedOutputSinkTest {
    @Test
    fun `stores up to configured bytes`() {
        val sink = BoundedOutputSink(4, StandardCharsets.UTF_8)

        sink.append("abcdef".toByteArray(), 0, 6)

        assertEquals("abcd", sink.content())
    }

    @Test
    fun `rejects non-positive limits`() {
        assertFailsWith<IllegalArgumentException> {
            BoundedOutputSink(0, StandardCharsets.UTF_8)
        }
    }

    @Test
    fun `appending in chunks respects remaining capacity`() {
        val sink = BoundedOutputSink(5, StandardCharsets.UTF_8)

        sink.append("abc".toByteArray(), 0, 3)
        sink.append("defg".toByteArray(), 0, 4)

        assertEquals("abcde", sink.content())
    }

    @Test
    fun `append after buffer full is ignored`() {
        val sink = BoundedOutputSink(2, StandardCharsets.UTF_8)

        sink.append("ab".toByteArray(), 0, 2)
        sink.append("cd".toByteArray(), 0, 2)

        assertEquals("ab", sink.content())
    }
}
