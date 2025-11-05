package com.github.ulviar.icli.fixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FixtureCliParserTest {
    private val parser = FixtureCliParser()

    @Test
    fun `parses defaults`() {
        val config = parser.parse(emptyArray())

        assertEquals(FixtureMode.SINGLE, config.mode())
        assertEquals(0, config.startupDelayMillis())
        assertEquals(25, config.runtimeBounds().minMillis())
        assertEquals(250, config.runtimeBounds().maxMillis())
        assertEquals(PayloadFormat.TEXT, config.payloadProfile().format())
        assertEquals(64, config.payloadProfile().size())
        assertEquals(StreamingStyle.SMOOTH, config.streamingProfile().style())
        assertEquals(3, config.streamingProfile().burstSize())
        assertEquals(250, config.streamingProfile().burstIntervalMillis())
        assertIs<FailurePlan.Never>(config.failurePlan())
        assertEquals(LogFormat.JSON, config.logFormat())
        assertEquals(NoiseLevel.QUIET, config.noiseProfile().stderrLevel())
        assertTrue(config.streamMaxChunks() >= 1)
    }

    @Test
    fun `parses explicit options`() {
        val config =
            parser.parse(
                arrayOf(
                    "--mode=line",
                    "--startup-ms=150",
                    "--runtime-min-ms=75",
                    "--runtime-max-ms=500",
                    "--payload=base64:32",
                    "--streaming=burst",
                    "--stream-burst-size=5",
                    "--stream-burst-interval-ms=400",
                    "--failure=at:3:exit=42",
                    "--seed=123",
                    "--log-format=text",
                    "--stderr-rate=loud",
                    "--echo-env",
                    "--stream-max-chunks=9",
                ),
            )

        assertEquals(FixtureMode.LINE, config.mode())
        assertEquals(150, config.startupDelayMillis())
        assertEquals(75, config.runtimeBounds().minMillis())
        assertEquals(500, config.runtimeBounds().maxMillis())
        assertEquals(PayloadFormat.BASE64, config.payloadProfile().format())
        assertEquals(32, config.payloadProfile().size())
        assertEquals(StreamingStyle.BURST, config.streamingProfile().style())
        assertEquals(5, config.streamingProfile().burstSize())
        assertEquals(400, config.streamingProfile().burstIntervalMillis())
        val failure = config.failurePlan()
        assertIs<FailurePlan.AtRequest>(failure)
        assertEquals(3, failure.requestIndex())
        assertEquals(42, failure.exitCode())
        assertEquals(123, config.seed())
        assertEquals(LogFormat.TEXT, config.logFormat())
        assertEquals(NoiseLevel.LOUD, config.noiseProfile().stderrLevel())
        assertTrue(config.echoEnvironment())
        assertEquals(9, config.streamMaxChunks())
    }
}
