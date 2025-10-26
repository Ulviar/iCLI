package com.github.ulviar.icli.testing

import com.github.ulviar.icli.client.ClientScheduler
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test-only scheduler that executes submitted tasks synchronously on the calling thread and records each callable.
 */
class ImmediateClientScheduler : ClientScheduler {
    val submitted: MutableList<Callable<*>> = CopyOnWriteArrayList()

    override fun <T> submit(task: Callable<T>): CompletableFuture<T> {
        submitted += task
        return try {
            CompletableFuture.completedFuture(task.call())
        } catch (ex: Throwable) {
            val failed = CompletableFuture<T>()
            failed.completeExceptionally(ex)
            failed
        }
    }

    override fun close() {
        // nothing to close; tasks run inline
    }
}
