package com.github.ulviar.icli.client.pooled

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationValueObjectsTest {
    @Test
    fun `affinity key trims and rejects blanks`() {
        val affinity = ConversationAffinity.key("  chat-42  ")

        assertEquals("chat-42", affinity.key())
        assertFailsWith<IllegalArgumentException> { ConversationAffinity.key("   ") }
    }

    @Test
    fun `affinity key cannot be read from sentinel`() {
        val affinity = ConversationAffinity.none()

        assertFailsWith<IllegalStateException> { affinity.key() }
    }

    @Test
    fun `reset reason trims and rejects blanks`() {
        val reset = ConversationReset.manual("  manual restart  ")

        assertEquals("manual restart", reset.reason())
        assertFailsWith<IllegalArgumentException> { ConversationReset.manual("  ") }
    }

    @Test
    fun `retirement descriptors enforce kind and reason invariants`() {
        val retirement = ConversationRetirement.unhealthy("  memory leak  ")

        assertEquals(ConversationRetirement.Kind.UNHEALTHY, retirement.kind())
        assertEquals("memory leak", retirement.reason())
        assertEquals("client requested retirement", ConversationRetirement.unspecified().reason())
        assertFailsWith<IllegalArgumentException> { ConversationRetirement.clientRequest("\t") }
    }
}
