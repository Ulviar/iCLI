package com.github.ulviar.icli.core.pool.api

import com.github.ulviar.icli.core.CommandDefinition
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessPoolConfigBuilderTest {
    @Test
    fun leaseTimeoutRejectsNegativeDuration() {
        val builder = ProcessPoolConfig.builder(COMMAND)

        assertFailsWith<IllegalArgumentException> {
            builder.leaseTimeout(Duration.ofSeconds(-1))
        }
    }

    @Test
    fun requestTimeoutRejectsNegativeDuration() {
        val builder = ProcessPoolConfig.builder(COMMAND)

        assertFailsWith<IllegalArgumentException> {
            builder.requestTimeout(Duration.ofSeconds(-1))
        }
    }

    @Test
    fun destroyProcessTreeOverrideAppliesToWorkerOptions() {
        val config =
            ProcessPoolConfig
                .builder(COMMAND)
                .destroyProcessTree(false)
                .build()

        assertFalse(config.destroyProcessTree())
        assertFalse(config.workerOptions().destroyProcessTree())
    }

    @Test
    fun invariantChecksEnabledDefaultsToTrueAndCanBeDisabled() {
        val defaults = ProcessPoolConfig.builder(COMMAND).build()
        assertTrue(defaults.invariantChecksEnabled())

        val disabled =
            ProcessPoolConfig
                .builder(COMMAND)
                .invariantChecksEnabled(false)
                .build()
        assertFalse(disabled.invariantChecksEnabled())
    }

    private companion object {
        private val COMMAND = CommandDefinition.of(listOf("fake"))
    }
}
