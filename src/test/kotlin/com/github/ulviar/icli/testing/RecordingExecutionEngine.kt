package com.github.ulviar.icli.testing

import com.github.ulviar.icli.core.CommandDefinition
import com.github.ulviar.icli.core.ExecutionOptions
import com.github.ulviar.icli.core.InteractiveSession
import com.github.ulviar.icli.core.ProcessEngine
import com.github.ulviar.icli.core.ProcessResult
import java.time.Duration
import java.util.ArrayDeque
import java.util.Optional

class RecordingExecutionEngine : ProcessEngine {
    var lastRunSpec: CommandDefinition? = null
        private set
    var lastRunOptions: ExecutionOptions? = null
        private set
    var lastSessionSpec: CommandDefinition? = null
        private set
    var lastSessionOptions: ExecutionOptions? = null
        private set
    val sessionInvocations: MutableList<CommandDefinition> = mutableListOf()
    val sessionStartFailures: ArrayDeque<RuntimeException> = ArrayDeque()

    var runResponse: ProcessResult =
        ProcessResult(0, "ok", "", Optional.of(Duration.ZERO))

    var sessionHandleFactory: () -> InteractiveSession = {
        throw UnsupportedOperationException("Session handle factory not configured")
    }

    override fun run(
        spec: CommandDefinition,
        options: ExecutionOptions,
    ): ProcessResult {
        lastRunSpec = spec
        lastRunOptions = options
        return runResponse
    }

    override fun startSession(
        spec: CommandDefinition,
        options: ExecutionOptions,
    ): InteractiveSession {
        sessionInvocations += spec
        lastSessionSpec = spec
        lastSessionOptions = options
        if (sessionStartFailures.isNotEmpty()) {
            throw sessionStartFailures.removeFirst()
        }
        return sessionHandleFactory()
    }
}
