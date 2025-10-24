package com.github.ulviar.icli.core

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandDefinitionTest {
    @Test
    fun `builder copies command and environment defensively`() {
        val builder =
            CommandDefinition
                .builder()
                .command(listOf("/bin/echo", "hello"))
                .workingDirectory(Paths.get("/tmp"))
                .putEnvironment("FOO", "bar")

        val spec = builder.build()
        val command = spec.command()
        val env = spec.environment()

        assertEquals(listOf("/bin/echo", "hello"), command)
        assertEquals(mapOf("FOO" to "bar"), env)

        assertFailsWith<UnsupportedOperationException> { command.add("oops") }
        assertFailsWith<UnsupportedOperationException> { env["BAR"] = "baz" }
    }

    @Test
    fun `shell defaults to none`() {
        val spec = CommandDefinition.builder().command("echo", "hi").build()
        assertEquals(ShellConfiguration.none(), spec.shell())
    }

    @Test
    fun `throws when command is empty`() {
        val builder = CommandDefinition.builder()
        assertFailsWith<IllegalArgumentException> { builder.build() }
    }

    @Test
    fun `builder allows resetting command`() {
        val spec =
            CommandDefinition
                .builder()
                .command("first")
                .command("second", "arg")
                .build()
        assertEquals(listOf("second", "arg"), spec.command())
        assertNull(spec.workingDirectory())
        assertTrue(spec.environment().isEmpty())
    }
}
