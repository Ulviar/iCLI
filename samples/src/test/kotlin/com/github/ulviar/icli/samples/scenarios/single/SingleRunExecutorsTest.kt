package com.github.ulviar.icli.samples.scenarios.single

import com.github.ulviar.icli.samples.fixtures.FakeSingleRunInvocations
import com.github.ulviar.icli.samples.real.RealToolInvocations
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SingleRunExecutorsTest {
    private val executors = SingleRunExecutors.defaultExecutors()

    @Test
    fun `fake process executes successfully across adapters`() {
        val invocation = FakeSingleRunInvocations.success(stdout = "fake-success")
        executors.forEach { executor ->
            val result = executor.execute(invocation)
            assertTrue(
                result.exitCode() == 0 && result.error() == null,
                "${executor.id()} failed for fake process: ${result.error()}",
            )
            val combined = result.stdout() + result.stderr()
            assertTrue(combined.contains("fake-success"), "${executor.id()} did not capture stdout")
        }
    }

    @Test
    fun `java version command completes for every adapter`() {
        val invocation = RealToolInvocations.javaVersion()
        executors.forEach { executor ->
            val result = executor.execute(invocation)
            assertTrue(
                result.success(),
                "${executor.id()} exited with ${result.exitCode()} (error=${result.error()})",
            )
            val combined = (result.stdout() + result.stderr()).lowercase()
            assertTrue(
                combined.contains("java") || combined.contains("openjdk"),
                "${executor.id()} did not capture java version output",
            )
        }
    }
}
