package sian.grafit.command.executor

import sian.utils.os.OsType
import sian.utils.os.OsUtils
import java.io.BufferedReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Реализация интерфейса [CommandExecutor] для выполнения команд в интерактивном режиме.
 *
 * Класс не предназначен для работы в многопоточной среде.
 *
 * @property timeout максимальное время ожидания выполнения команды
 * @property unit единица измерения времени ожидания
 */
class InteractiveCommandExecutor(
    private val timeout: Long = 5,
    private val unit: TimeUnit = TimeUnit.SECONDS
) : CommandExecutor {

    private companion object {
        /**
         * При большом числе выполненных команд начинают проявляться проблемы с памятью. Скорее всего причины в
         * буферах для чтения и записи командной строки, а также в накоплении информации внутри
         * [java.util.concurrent.ExecutorService].
         *
         * Самый простой способ борьбы с утечками — иногда пересоздавать процессы, закрывая все используемые активным
         * процессом ресурсы. В этом классе перезапуск происходит после выполнения заданного числа команд в рамках
         * одного процесса.
         *
         * Так как этот класс сделан с ориентацией на использование MyStem, выбрано достаточно большое число
         * выполняемых до перезапуска команд, чтобы минимизировать потери, связанные с инициализацией MyStem.
         */
        const val MAX_COMMAND_BEFORE_RESTART = 10000
    }

    private var activeProcess: ActiveProcess? = null
    private val commandCounter = AtomicInteger()
    private val timeoutScheduler = TimeoutScheduler()

    override fun execute(command: String, args: List<String>, input: String): CommandExecutionResult {
        val process: ActiveProcess = getProcess(command, args)
        if (command != process.currentCommand || args != process.currentArgs) {
            throw IllegalStateException(
                "Переданная команда ${buildCommandName(command, args)} не совпадает с уже исполняемой командой. " +
                        "Исполняемая команда: ${buildCommandName(process.currentCommand, process.currentArgs)}."
            )
        }
        try {
            process.inputWriter.write(input)
            process.inputWriter.newLine()
            process.inputWriter.flush()
            /*
             * При использовании Lucene Test Framework возникает проблема с использованием метода
             * CompletableFuture.orTimeout. Проблема возникает из-за того, что реализация этого метода приводит к
             * появлению нового статического ExecutorService, на который Lucene Test Framework реагирует как на
             * проблему появления «утёкшего» потока. Отказываться от Lucene Test Framework не хочется, а как-то
             * исправить отслеживание потоков в тестовом фреймворке сложно, поэтому проще всего оказалось сделать
             * кастомное решение, аналогичное логике работы метода CompletableFuture.orTimeout.
             */
            val outputFuture = timeoutScheduler.withTimeout(
                CompletableFuture.supplyAsync({ readOutput(process.outputReader) }, process.outputService),
                timeout, unit
            )
            val errorOutputFuture = timeoutScheduler.withTimeout(
                CompletableFuture.supplyAsync({ readErrorOutput(process.errorReader) }, process.errorService),
                timeout, unit
            )
            val result = CompletableFuture.anyOf(outputFuture, errorOutputFuture)
                .thenApply { it as String }
                .join()
            return if (outputFuture.isDone) {
                CommandExecutionResult(0, result.toString(), "")
            } else {
                CommandExecutionResult(1, null, result)
            }
        } catch (e: CompletionException) {
            if (e.cause is TimeoutException) {
                return CommandExecutionResult(
                    1,
                    null,
                    "Команда ${buildCommandName(command, args)} с входом \"$input\" выполнялась дольше допустимого"
                )
            }
            return CommandExecutionResult(
                null,
                null,
                e.message ?: "Ошибка при работе с командой ${buildCommandName(command, args)}"
            )
        } catch (e: Exception) {
            return CommandExecutionResult(
                null,
                null,
                e.message ?: "Ошибка при работе с командой ${buildCommandName(command, args)}"
            )
        }
    }

    private fun getProcess(command: String, args: List<String>): ActiveProcess {
        if (commandCounter.incrementAndGet() < MAX_COMMAND_BEFORE_RESTART) {
            if (activeProcess != null) {
                return activeProcess!!
            }
            val startedProcess = startProcess(command, args)
            activeProcess = startedProcess
            return startedProcess
        }
        val restartedProcess = restartProcess(command, args, activeProcess)
        activeProcess = restartedProcess
        return restartedProcess
    }

    private fun startProcess(command: String, args: List<String>): ActiveProcess {
        val processBuilder = if (OsUtils.getType() == OsType.WINDOWS) {
            ProcessBuilder(mutableListOf("cmd", "/c") + command + args)
        } else {
            ProcessBuilder(mutableListOf(command) + args)
        }
        val newProcess = processBuilder.start()
        val inputWriter = newProcess.outputStream.bufferedWriter()
        val outputReader = newProcess.inputStream.bufferedReader()
        val errorReader = newProcess.errorStream.bufferedReader()
        return ActiveProcess(newProcess, inputWriter, outputReader, errorReader, command, args)
    }

    private fun restartProcess(command: String, args: List<String>, activeProcess: ActiveProcess?): ActiveProcess {
        activeProcess?.stop()
        timeoutScheduler.restart()
        commandCounter.set(0)
        return startProcess(command, args)
    }

    private fun buildCommandName(command: String, args: List<String>): String {
        return if (args.isEmpty()) {
            "\"$command\""
        } else {
            "\"$command ${args.joinToString(" ")}\""
        }
    }

    private fun readOutput(outputReader: BufferedReader): String {
        val result = StringBuilder()
        while (!outputReader.ready()) {
            Thread.sleep(0, 100)
        }
        while (outputReader.ready()) {
            result.append(outputReader.readLine())
        }
        return result.toString().trim()
    }

    private fun readErrorOutput(errorOutput: BufferedReader): String {
        val result = StringBuilder()
        while (!errorOutput.ready()) {
            Thread.sleep(0, 100)
        }
        while (errorOutput.ready()) {
            result.append(errorOutput.readLine())
        }
        return result.toString().trim()
    }

    /**
     * Останавливает активный процесс и освобождает ресурсы.
     */
    fun stop() {
        activeProcess?.stop()
        timeoutScheduler.stop()
        activeProcess = null
        commandCounter.set(0)
    }

    internal fun isRunning(): Boolean {
        return activeProcess?.process?.isAlive ?: false
    }
}