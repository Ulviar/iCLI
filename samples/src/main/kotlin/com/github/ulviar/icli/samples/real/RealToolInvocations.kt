package com.github.ulviar.icli.samples.real

import com.github.ulviar.icli.samples.scenarios.single.CommandInvocation
import com.github.ulviar.icli.samples.support.JavaCommandLocator
import java.time.Duration

object RealToolInvocations {
    fun javaVersion(): CommandInvocation =
        CommandInvocation
            .builder()
            .command(listOf(JavaCommandLocator.javaExecutable().toString(), "-version"))
            .mergeErrorIntoOutput(true)
            .timeout(Duration.ofSeconds(15))
            .build()
}
