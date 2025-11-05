package com.github.ulviar.icli.fixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureStreamingModeTest {
    @Test
    fun `stream mode emits bounded chunks`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=stream",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:5",
                    "--streaming=chunked",
                    "--stream-max-chunks=3",
                    "--seed=3",
                ),
            )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.lines().any { it.startsWith("READY-STREAM") }, result.stdout)
        val chunkCount = result.stdout.lines().count { it.startsWith("CHUNK") }
        assertEquals(3, chunkCount, result.stdout)
        assertTrue(result.stdout.contains("STREAM-COMPLETE"), result.stdout)
    }

    @Test
    fun `stop command terminates stream early`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=stream",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:5",
                    "--stream-max-chunks=5",
                    "--seed=5",
                ),
                stdinLines = listOf("STOP"),
            )

        assertEquals(0, result.exitCode)
        val chunkCount = result.stdout.lineSequence().count { it.startsWith("CHUNK") }
        assertEquals(0, chunkCount, result.stdout)
        assertTrue(result.stdout.contains("STREAM-COMPLETE command"), result.stdout)
    }

    @Test
    fun `pause and resume apply overrides`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=stream",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                    "--payload=text:2",
                    "--stream-max-chunks=1",
                    "--seed=9",
                ),
                stdinLines =
                    listOf(
                        "PAUSE",
                        """{"label":"paused","payload":{"type":"text","size":4}}""",
                        "RESUME",
                    ),
            )

        val chunkLines = result.stdout.lineSequence().filter { it.startsWith("CHUNK") }.toList()
        assertEquals(1, chunkLines.size, result.stdout)
        assertTrue(chunkLines.first().contains("-paused"), result.stdout)
        assertTrue(result.stdout.contains("STREAM-COMPLETE max-chunks"), result.stdout)
    }

    @Test
    fun `fail command returns requested exit code`() {
        val result =
            FixtureHarness.runFixture(
                listOf(
                    "--mode=stream",
                    "--runtime-min-ms=0",
                    "--runtime-max-ms=0",
                ),
                stdinLines = listOf("FAIL 77"),
            )

        assertEquals(77, result.exitCode)
        val chunkLines = result.stdout.lineSequence().filter { it.startsWith("CHUNK") }.toList()
        assertTrue(chunkLines.isEmpty(), result.stdout)
    }
}
