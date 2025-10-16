package sian.grafit.command.executor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandExecutionResultTest {

    @Test
    fun `command executes successfully`() {
        val result = CommandExecutionResult(exitCode = 0, output = "Success", errorMessage = "")
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
    }

    @Test
    fun `command executes with error`() {
        val result = CommandExecutionResult(exitCode = 1, output = "", errorMessage = "Some error")
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `command does not execute due to error`() {
        val result = CommandExecutionResult(
            exitCode = null,
            output = null,
            errorMessage = RuntimeException().toString()
        )
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `command executes with error and empty error message`() {
        val result = CommandExecutionResult(exitCode = 1, output = "", errorMessage = "")
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `command executes with error and result`() {
        val result = CommandExecutionResult(exitCode = 1, output = "Partial success", errorMessage = "Some error")
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }
}