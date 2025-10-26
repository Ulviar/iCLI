package com.github.ulviar.icli.testing.processes

import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Small deterministic CLI used in tests to simulate different process behaviours (stdout/stderr output, sleeps,
 * failure codes, environment/working-directory inspection).
 */
object TestProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        var exitCode = 0
        var i = 0

        while (i < args.size) {
            when (val arg = args[i++]) {
                "--stdout" -> println(args[i++])
                "--stderr" -> System.err.println(args[i++])
                "--repeat" -> {
                    val chunk = args[i++]
                    val repetitions = args[i++].toInt()
                    repeat(repetitions) {
                        print(chunk)
                    }
                    println()
                }
                "--sleep-ms" -> {
                    val delay = args[i++].toLong()
                    try {
                        Thread.sleep(delay)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                "--exit" -> exitCode = args[i++].toInt()
                "--print-env" -> {
                    val key = args[i++]
                    println(System.getenv(key) ?: "")
                }
                "--print-cwd" ->
                    println(
                        Paths
                            .get("")
                            .toAbsolutePath()
                            .normalize()
                            .toString(),
                    )
                else -> throw IllegalArgumentException("Unknown arg: $arg")
            }
        }

        System.out.flush()
        System.err.flush()
        exitProcess(exitCode)
    }
}
