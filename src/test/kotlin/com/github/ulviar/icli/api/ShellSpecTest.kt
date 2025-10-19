package com.github.ulviar.icli.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ShellSpecTest {
    @Test
    fun `builder defaults to empty command with default style`() {
        val spec = ShellSpec.builder().build()

        assertEquals(emptyList(), spec.command())
        assertEquals(ShellSpec.InvocationStyle.DEFAULT, spec.style())
    }

    @Test
    fun `builder applies custom command and style`() {
        val spec =
            ShellSpec
                .builder()
                .command("bash", "-lc")
                .style(ShellSpec.InvocationStyle.LOGIN)
                .build()

        assertEquals(listOf("bash", "-lc"), spec.command())
        assertEquals(ShellSpec.InvocationStyle.LOGIN, spec.style())
    }
}
