package com.github.ulviar.icli.fixture

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal object FixtureHarness {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    fun runFixture(
        args: List<String>,
        stdinLines: List<String> = emptyList(),
    ): Result {
        val application = FixtureApplication()
        val inputData =
            if (stdinLines.isEmpty()) {
                ByteArray(0)
            } else {
                (stdinLines.joinToString(separator = "\n", postfix = "\n")).toByteArray(StandardCharsets.UTF_8)
            }
        val stdin = ByteArrayInputStream(inputData)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val exitCode = application.run(args.toTypedArray(), stdin, stdout, stderr)
        return Result(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8),
        )
    }
}
