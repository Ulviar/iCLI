package com.github.ulviar.icli.samples.fixtures

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation
import com.github.ulviar.icli.samples.support.JavaCommandLocator
import java.nio.file.Path
import java.time.Duration

object FakeSingleRunInvocations {
    private val javaExe: Path = JavaCommandLocator.javaExecutable()
    private val classpath: String = JavaCommandLocator.currentClasspath()
    private val mainClass = FakeSingleRunProcess::class.java.name

    fun success(
        stdout: String = "fake-output",
        stderr: String = "",
        exitCode: Int = 0,
        extraArgs: List<String> = emptyList(),
    ): CommandInvocation {
        val base = listOf(javaExe.toString(), "-cp", classpath, mainClass, stdout, stderr, exitCode.toString())
        return CommandInvocation
            .builder()
            .command(base + extraArgs)
            .timeout(Duration.ofSeconds(10))
            .build()
    }

    fun timeout(duration: Duration): CommandInvocation {
        val base =
            listOf(
                javaExe.toString(),
                "-cp",
                classpath,
                mainClass,
                "pending",
                "",
                "0",
                duration.plusSeconds(10).toMillis().toString(),
            )
        return CommandInvocation
            .builder()
            .command(base)
            .timeout(duration)
            .build()
    }
}
