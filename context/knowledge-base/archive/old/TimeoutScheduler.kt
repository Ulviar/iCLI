package sian.grafit.command.executor

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Класс, который управляет тайм-аутами для асинхронных операций с использованием `CompletableFuture`. Он
 * предоставляет механизм, который позволяет добавлять ограничение времени к любому `CompletableFuture`, обеспечивая
 * завершение задачи в случае превышения заданного тайм-аута.
 *
 * Основная цель этого класса — предоставить альтернативу встроенному методу `CompletableFuture.orTimeout()`, который
 * может приводить к проблемам при тестировании.
 */
internal class TimeoutScheduler {

    private var scheduler: ScheduledExecutorService = scheduledExecutorService()

    fun <T> withTimeout(
        future: CompletableFuture<T>,
        timeout: Long,
        timeOutUnit: TimeUnit
    ): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val timeoutFuture: ScheduledFuture<*> = scheduler.schedule(
            { result.completeExceptionally(TimeoutException()) },
            timeout, timeOutUnit
        )
        future.whenComplete { value, exception ->
            if (exception == null) {
                result.complete(value)
            } else {
                result.completeExceptionally(exception)
            }
            timeoutFuture.cancel(true)
        }
        return result
    }

    fun restart() {
        stop()
        scheduler = scheduledExecutorService()
    }

    fun stop() {
        scheduler.shutdownNow()
    }

    private fun scheduledExecutorService(): ScheduledExecutorService {
        val threadPool = ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory())
        threadPool.removeOnCancelPolicy = true
        return threadPool
    }
}