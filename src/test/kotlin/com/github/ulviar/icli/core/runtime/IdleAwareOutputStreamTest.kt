package com.github.ulviar.icli.core.runtime

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class IdleAwareOutputStreamTest {
    @Test
    fun `invokes callback after each operation`() {
        val delegate = ByteArrayOutputStream()
        val counter = AtomicInteger()
        val stream = IdleAwareOutputStream(delegate) { counter.incrementAndGet() }

        stream.write('a'.code)
        stream.write("bc".toByteArray(StandardCharsets.UTF_8))
        stream.flush()
        stream.close()

        assertEquals("abc", delegate.toString(StandardCharsets.UTF_8))
        assertEquals(4, counter.get())
    }
}
