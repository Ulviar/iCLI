package sian.grafit.command.executor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SingleCommandExecutorTest {

    @Test
    fun `command executes successfully`() {
        val command = "echo"
        val args = listOf("Hello")
        val input = ""

        val result = SingleCommandExecutor.execute(command, args, input)

        assertEquals("Hello", result.output)
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
    }

    @Test
    fun `command execution with invalid arguments results in error`() {
        val command = "cd"
        val args = listOf(")")
        val input = ""

        val result = SingleCommandExecutor.execute(command, args, input)

        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `nonexistent command results in error`() {
        val command = "nonexistentcommand"
        val args = listOf<String>()
        val input = ""

        val result = SingleCommandExecutor.execute(command, args, input)

        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `command executes successfully with stdin input`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val input = "This is a test input"

        val result = SingleCommandExecutor.execute(command, args, input)

        assertTrue(result.isSuccess())
        assertEquals(input, result.output)
        assertFalse(result.isError())
    }
}