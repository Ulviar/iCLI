package com.github.ulviar.icli.engine.pool.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferredWorkerTest {
    @Test
    fun `any preference reports no specific worker`() {
        val preference = PreferredWorker.any()

        assertFalse(preference.hasSpecificWorker())
        assertEquals("PreferredWorker[any]", preference.toString())
        assertFailsWith<IllegalStateException> { preference.workerId() }
    }

    @Test
    fun `specific preference exposes worker id`() {
        val preference = PreferredWorker.specific(42)

        assertTrue(preference.hasSpecificWorker())
        assertEquals(42, preference.workerId())
        assertEquals("PreferredWorker[42]", preference.toString())
    }

    @Test
    fun `specific preference rejects negative ids`() {
        assertFailsWith<IllegalArgumentException> { PreferredWorker.specific(-1) }
    }
}
