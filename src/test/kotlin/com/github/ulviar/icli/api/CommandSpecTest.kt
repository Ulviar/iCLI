package com.github.ulviar.icli.api

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandSpecTest {
    @Test
    fun `builder copies command and environment defensively`() {
        val builder =
            CommandSpec
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
        val spec = CommandSpec.builder().command("echo", "hi").build()
        assertEquals(ShellSpec.none(), spec.shell())
    }

    @Test
    fun `throws when command is empty`() {
        val builder = CommandSpec.builder()
        assertFailsWith<IllegalArgumentException> { builder.build() }
    }

    @Test
    fun `builder allows resetting command`() {
        val spec =
            CommandSpec
                .builder()
                .command("first")
                .command("second", "arg")
                .build()
        assertEquals(listOf("second", "arg"), spec.command())
        assertNull(spec.workingDirectory())
        assertTrue(spec.environment().isEmpty())
    }
}
