package com.github.ulviar.icli.fixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixtureLineModeTest {
    @Test
    fun `line mode handshake and json override`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=line",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:6",
                    "--seed=11",
                ),
                stdinLines =
                    listOf(
                        "PING",
                        "{\"payload\":{\"type\":\"text\",\"size\":4},\"label\":\"case-a\"}",
                        "EXIT",
                    ),
            )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.lines().any { it.startsWith("READY") }, result.stdout)
        assertTrue(result.stdout.contains("PONG"), result.stdout)
        assertTrue(result.stdout.contains("case-a"), result.stdout)
        assertTrue(result.stdout.contains("\"event\":\"line-response\""), result.stdout)
    }

    @Test
    fun `line mode supports inline streaming overrides`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=line",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:4",
                    "--stream-max-chunks=5",
                    "--seed=17",
                ),
                stdinLines =
                    listOf(
                        """{"mode":"stream","chunks":2,"label":"inline","payload":{"type":"text","size":3}}""",
                        "EXIT",
                    ),
            )

        assertEquals(0, result.exitCode)
        val chunkLines = result.stdout.lineSequence().filter { it.startsWith("CHUNK") }.toList()
        assertEquals(2, chunkLines.size, result.stdout)
        assertTrue(chunkLines.all { it.contains("-inline") }, result.stdout)
        assertTrue(result.stdout.contains("STREAM-COMPLETE inline-request"), result.stdout)
    }

    @Test
    fun `line mode fail command returns exit code`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=line",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                ),
                stdinLines = listOf("FAIL 23"),
            )

        assertEquals(23, result.exitCode)
        assertFalse(result.stdout.lineSequence().any { it.startsWith("RESULT") }, result.stdout)
    }
}
