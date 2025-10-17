package sian.grafit.command.executor

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class ActiveProcess(
    val process: Process,
    val inputWriter: BufferedWriter,
    val outputReader: BufferedReader,
    val errorReader: BufferedReader,
    val currentCommand: String,
    val currentArgs: List<String>,
) {

    val outputService: ExecutorService = Executors.newFixedThreadPool(1, Thread.ofVirtual().factory())
    val errorService: ExecutorService = Executors.newFixedThreadPool(1, Thread.ofVirtual().factory())

    fun stop() {
        outputService.shutdown()
        errorService.shutdown()
        closeIgnoringIoException(inputWriter)
        closeIgnoringIoException(outputReader)
        closeIgnoringIoException(errorReader)
        process.destroy()
    }

    /**
     * В случае возникновения [IOException] некоторые ресурсы могут быть уже закрыты, что приведет к исключению при
     * повторной попытке закрытия. Поэтому закрытие ресурсов выполнено по аналогии с методом
     * java.lang.ProcessImpl.destroy.
     */
    private fun closeIgnoringIoException(resource: Closeable) {
        try {
            resource.close()
        } catch (ignored: IOException) {
        }
    }
}