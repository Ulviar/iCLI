package com.github.ulviar.icli.client

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientSchedulerTest {
    @Test
    fun `virtual thread scheduler runs tasks on virtual threads`() {
        ClientSchedulers.virtualThreads().use { scheduler ->
            val future = scheduler.submit { Thread.currentThread().isVirtual }
            assertTrue(future.get())
        }
    }

    @Test
    fun `cancelling task interrupts execution`() {
        ClientSchedulers.virtualThreads().use { scheduler ->
            val started = CountDownLatch(1)
            val interrupted = AtomicBoolean(false)
            val interruptedSignal = CountDownLatch(1)

            val future =
                scheduler.submit {
                    started.countDown()
                    try {
                        Thread.sleep(Duration.ofSeconds(1).toMillis())
                        false
                    } catch (ignored: InterruptedException) {
                        interrupted.set(true)
                        interruptedSignal.countDown()
                        throw ignored
                    }
                }

            started.await()
            future.cancel(true)
            assertTrue(future.isCancelled)
            interruptedSignal.await(1, TimeUnit.SECONDS)
            assertTrue(interrupted.get())
        }
    }

    @Test
    fun `usingExecutorService shuts down executor when requested`() {
        val executor = Executors.newSingleThreadExecutor()
        ClientSchedulers.usingExecutorService(executor, true).use { scheduler ->
            val future = scheduler.submit(Callable { 42 })
            assertEquals(42, future.get())
        }
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `usingExecutorService can leave executor running`() {
        val executor = Executors.newSingleThreadExecutor()
        ClientSchedulers.usingExecutorService(executor, false).use { scheduler ->
            val future = scheduler.submit(Callable { "ok" })
            assertEquals("ok", future.get())
        }
        assertFalse(executor.isShutdown)
        executor.shutdown()
    }
}
