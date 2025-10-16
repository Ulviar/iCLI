package sian.grafit.command.executor

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class InteractiveCommandExecutorPoolTest {

    private lateinit var defaultPool: InteractiveCommandExecutorPool

    @BeforeEach
    fun setup() {
        defaultPool = InteractiveCommandExecutorPool(
            maxPoolSize = 4,
            maxIdleTime = 1,
            maxIdleTimeUnit = TimeUnit.SECONDS,
            commandTimeout = 5,
            commandTimeoutUnit = TimeUnit.SECONDS
        )
    }

    @AfterEach
    fun tearDown() {
        defaultPool.stop()
    }

    @Test
    fun `test execute command`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()
        val input = "Test input"

        val result = defaultPool.execute(command, args, input)

        assertEquals("Test input", result.output)
        assertTrue(result.isSuccess())
        assertFalse(result.isError())
    }

    @Test
    fun `test pool handles multiple commands sequentially`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        repeat(10) {
            val input = ThreadLocalRandom.current().nextInt().toString()

            val result = defaultPool.execute(command, args, input)

            assertEquals(input, result.output)
            assertTrue(result.isSuccess())
            assertFalse(result.isError())
        }
    }

    @Test
    fun `test multiple command executions`() {
        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        val executorService = Executors.newFixedThreadPool(10)
        val tasks = List(20) {
            executorService.submit {
                val input = ThreadLocalRandom.current().nextInt().toString()

                val result = defaultPool.execute(command, args, input)

                assertEquals(input, result.output)
                assertTrue(result.isSuccess())
                assertFalse(result.isError())
            }
        }
        tasks.forEach { it.get() }
    }

    @Test
    fun `test multiple command executions after idle time`() {
        val shortIdleTimePool = InteractiveCommandExecutorPool(
            maxPoolSize = 4,
            maxIdleTime = 2,
            maxIdleTimeUnit = TimeUnit.MILLISECONDS
        )

        val command = InteractiveCommand.NAME
        val args = emptyList<String>()

        val executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        var tasks = List(10) {
            executorService.submit {
                val input = ThreadLocalRandom.current().nextInt().toString()

                val result = shortIdleTimePool.execute(command, args, input)

                assertEquals(input, result.output)
                assertTrue(result.isSuccess())
                assertFalse(result.isError())
            }
        }
        tasks.forEach { it.get() }

        Thread.sleep(10)

        tasks = List(10) {
            executorService.submit {
                val input = ThreadLocalRandom.current().nextInt().toString()

                val result = shortIdleTimePool.execute(command, args, input)

                assertEquals(input, result.output)
                assertTrue(result.isSuccess())
                assertFalse(result.isError())
            }
        }
        tasks.forEach { it.get() }
        executorService.shutdown()
    }

    @Test
    fun `test stop pool`() {
        defaultPool.stop()

        val result = defaultPool.execute(InteractiveCommand.NAME, emptyList(), "Hello")

        assertTrue(result.isError())
        assertEquals(result.errorMessage, "Пул процессов закрыт")
    }

    @Test
    fun `test execute command with error`() {
        val command = InteractiveCommand.NAME
        val args = listOf("-z")
        val input = "This input won't matter"

        val result = defaultPool.execute(command, args, input)

        assertFalse(result.isSuccess())
        assertTrue(result.isError())
        assertNotNull(result.errorMessage)
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
        val executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val tasks = mutableListOf<Future<*>>()
        repeat(10_000) {
            tasks.add(executorService.submit {
                repeat(10) {
                    val input = ThreadLocalRandom.current().nextInt().toString()

                    val result = defaultPool.execute(command, args, input)

                    assertEquals(input, result.output)
                    assertTrue(result.isSuccess())
                    assertFalse(result.isError())
                }
            })
        }
        tasks.forEach { it.get() }
        assertTrue(executorService.shutdownNow().isEmpty())
    }
}