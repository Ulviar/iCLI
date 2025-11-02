package com.github.ulviar.icli.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellConfigurationTest {
    @Test
    fun `builder defaults to empty command with default style`() {
        val spec = ShellConfiguration.builder().build()

        assertEquals(emptyList(), spec.command())
        assertEquals(ShellConfiguration.InvocationStyle.DEFAULT, spec.style())
    }

    @Test
    fun `builder applies custom command and style`() {
        val spec =
            ShellConfiguration
                .builder()
                .command("bash", "-lc")
                .style(ShellConfiguration.InvocationStyle.LOGIN)
                .build()

        assertEquals(listOf("bash", "-lc"), spec.command())
        assertEquals(ShellConfiguration.InvocationStyle.LOGIN, spec.style())
    }
}
