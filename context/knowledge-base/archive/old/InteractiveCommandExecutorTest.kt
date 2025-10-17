package sian.grafit.command.executor

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sian.utils.os.OsType
import sian.utils.os.OsUtils
import java.util.concurrent.TimeUnit

class InteractiveCommandExecutorTest {

    private lateinit var executor: InteractiveCommandExecutor

    @BeforeEach
    fun setup() {
        executor = InteractiveCommandExecutor()
    }

    @AfterEach
    fun tearDown() {
        executor.stop()
    }

    @Test
    fun `interactive command is running after execution`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        val result = executor.execute(command, args, "Hello, World!")

        assertNotNull(result)
        assertTrue(executor.isRunning())
    }

    @Test
    fun `interactive command sends and receives data`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val input = "Hello, World!"

        val result = executor.execute(command, args, input)

        assertEquals(input, result.output)
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
    }

    @Test
    fun `interactive command sends and receives data from multiple inputs`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val resultStringBuilder = StringBuilder()

        executor.execute(command, args, "1").output?.let { resultStringBuilder.append(it) }
        executor.execute(command, args, "2").output?.let { resultStringBuilder.append(it) }
        executor.execute(command, args, System.lineSeparator()).output?.let { resultStringBuilder.append(it) }
        executor.execute(command, args, "4" + System.lineSeparator()).output?.let { resultStringBuilder.append(it) }
        executor.execute(command, args, "5").output?.let { resultStringBuilder.append(it) }

        assertEquals("1245", resultStringBuilder.toString())
    }

    @Test
    fun `interactive command handles multiple lines of input`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val input = "Hello,\nWorld!\nThis is a test."

        executor.execute(command, args, "")
        val result = executor.execute(command, args, input)

        assertEquals(input.replace("\n", ""), result.output)
    }

    @Test
    fun `interactive command handles errors correctly`() {
        val command = InteractiveCommand.NAME
        val args = listOf("-z")
        val input = "Hello, World!"

        val result = executor.execute(command, args, input)

        assertFalse(result.isSuccess())
        assertTrue(result.isError())
    }

    @Test
    fun `throws error if different command is executed`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        executor.execute(command, args, "")

        val exception = assertThrows<IllegalStateException> {
            executor.execute("ls", listOf("-l"), "")
        }
        assertEquals(
            "Переданная команда \"ls -l\" не совпадает с уже исполняемой командой. Исполняемая команда: \"$command\".",
            exception.message
        )
    }

    @Test
    fun `interactive command terminates correctly`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        executor.execute(command, args, "")
        assertTrue(executor.isRunning())

        executor.stop()
        assertFalse(executor.isRunning())
    }

    @Test
    fun `io error handling with nonexistent file`() {
        val command = if (OsUtils.getType() == OsType.WINDOWS) "type" else "cat"
        val args = listOf("/nonexistentfile")
        val input = ""

        val result = executor.execute(command, args, input)
        assertFalse(result.isSuccess())
        assertTrue(result.isError())
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `command exceeds timeout`() {
        val command = "sleep"
        val args = listOf("5")
        val input = ""

        val executorWithShortTimeout = InteractiveCommandExecutor(1, TimeUnit.NANOSECONDS)
        val result = executorWithShortTimeout.execute(command, args, input)

        assertFalse(result.isSuccess())
        assertTrue(result.isError())
        assertEquals("Команда \"sleep 5\" с входом \"\" выполнялась дольше допустимого", result.errorMessage)
    }

    /**
     * Тест проверяет, что даже при большом числе выполненных команд память не утекает и код продолжает работать
     * корректно.
     *
     * Так как тест выполняется достаточно долго, он отключён. При необходимости можно убрать аннотацию и запустить
     * тест.
     */
    @Disabled
    @Test
    fun `interactive command sends and receives data multiple times`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val input = "Hello, World!"

        repeat(100_000) {
            val result = executor.execute(command, args, input)

            assertEquals(input, result.output)
            assertTrue(result.isSuccess())
            assertFalse(result.isError())
        }
    }
}