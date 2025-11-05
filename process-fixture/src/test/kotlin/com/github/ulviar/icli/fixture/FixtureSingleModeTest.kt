package com.github.ulviar.icli.fixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureSingleModeTest {
    @Test
    fun `single run emits payload and diagnostics`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=single",
                    "--startup-ms=0",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:8",
                    "--seed=7",
                    "--log-format=json",
                ),
            )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"event\":\"startup\""), result.stdout)
        assertTrue(result.stdout.contains("\"event\":\"request-start\""), result.stdout)
        assertTrue(result.stdout.contains("\"event\":\"request-complete\""), result.stdout)
        assertTrue(result.stdout.contains("PAYLOAD"), result.stdout)
    }
}
