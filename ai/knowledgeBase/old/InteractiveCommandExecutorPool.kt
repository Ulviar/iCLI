package sian.grafit.command.executor

import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Реализация интерфейса [CommandExecutor] для выполнения команд в интерактивном режиме в многопоточной среде.
 *
 * Пул исполнителей интерактивных команд, позволяющий работать с интерактивными командами в многопоточной среде.
 *
 * Создаёт и поддерживает пул исполняющих команды процессов заданного размера. Процессы создаются в пуле только если все
 * уже созданные ранее процессы используются. Если же пул уже достиг своего максимального размера, то для использования
 * процесса необходимо будет дождаться освобождения одного из процессов пула.
 *
 * Если процессы пула не используются дольше заданного времени, то они уничтожаются, освобождая ресурсы.
 *
 * @param maxPoolSize максимальное количество активных процессов в пуле
 * @param maxIdleTime максимальное время простоя процесса до его удаления из пула (по умолчанию 1)
 * @param maxIdleTimeUnit единица измерения максимального времени простоя (по умолчанию [TimeUnit.MINUTES])
 * @param commandTimeout максимальное время выполнения команды (по умолчанию 5)
 * @param commandTimeoutUnit единица измерения времени выполнения команды (по умолчанию [TimeUnit.SECONDS])
 */
class InteractiveCommandExecutorPool(
    maxPoolSize: Int,
    maxIdleTime: Long = 1,
    maxIdleTimeUnit: TimeUnit = TimeUnit.MINUTES,
    commandTimeout: Long = 5,
    commandTimeoutUnit: TimeUnit = TimeUnit.SECONDS
) : CommandExecutor {

    private val pool: GenericObjectPool<InteractiveCommandExecutor>

    init {
        val config = GenericObjectPoolConfig<InteractiveCommandExecutor>().apply {

            maxTotal = maxPoolSize
            minIdle = 0
            maxIdle = maxPoolSize
            timeBetweenEvictionRuns = Duration.of(maxIdleTime, maxIdleTimeUnit.toChronoUnit()).dividedBy(2)
            minEvictableIdleDuration = Duration.of(maxIdleTime, maxIdleTimeUnit.toChronoUnit())
            numTestsPerEvictionRun = maxPoolSize
        }
        pool = GenericObjectPool(InteractiveCommandExecutorFactory(commandTimeout, commandTimeoutUnit), config)
    }

    override fun execute(command: String, args: List<String>, input: String): CommandExecutionResult {
        if (pool.isClosed) {
            return CommandExecutionResult(null, null, "Пул процессов закрыт")
        }
        val executor = pool.borrowObject()
        try {
            return executor.execute(command, args, input)
        } finally {
            pool.returnObject(executor)
        }
    }

    /**
     * Завершает работу пула исполнителей, освобождая все ресурсы.
     */
    fun stop() {
        pool.close()
    }

    private class InteractiveCommandExecutorFactory(
        private val commandTimeout: Long,
        private val commandTimeoutUnit: TimeUnit
    ) : BasePooledObjectFactory<InteractiveCommandExecutor>() {

        override fun create(): InteractiveCommandExecutor {
            return InteractiveCommandExecutor(commandTimeout, commandTimeoutUnit)
        }

        override fun wrap(obj: InteractiveCommandExecutor): PooledObject<InteractiveCommandExecutor> {
            return DefaultPooledObject(obj)
        }

        override fun destroyObject(p: PooledObject<InteractiveCommandExecutor>?) {
            p?.getObject()?.stop()
        }
    }
}