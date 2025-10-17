package sian.grafit.command.executor

import sian.utils.os.OsType
import sian.utils.os.OsUtils
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Объект для выполнения консольных команд, завершающий процесс после выполнения команды.
 */
object SingleCommandExecutor : CommandExecutor {

    override fun execute(command: String, args: List<String>, input: String): CommandExecutionResult {
        return try {
            val processBuilder = if (OsUtils.getType() == OsType.WINDOWS) {
                ProcessBuilder(mutableListOf("cmd", "/c") + command + args)
            } else {
                ProcessBuilder(mutableListOf(command) + args)
            }
            val process = processBuilder.start()
            if (input.isNotEmpty()) {
                process.outputStream.bufferedWriter().use { it.write(input) }
            }
            val result = InputStreamReader(process.inputStream).use { streamReader ->
                BufferedReader(streamReader).use { bufferedReader ->
                    bufferedReader.readText()
                }
            }
            val errorMessage = InputStreamReader(process.errorStream).use { streamReader ->
                BufferedReader(streamReader).use { bufferedReader ->
                    bufferedReader.readText()
                }
            }
            val exitCode = process.waitFor()
            CommandExecutionResult(exitCode, result.trim(), errorMessage)
        } catch (e: Exception) {
            CommandExecutionResult(null, null, e.toString())
        }
    }
}