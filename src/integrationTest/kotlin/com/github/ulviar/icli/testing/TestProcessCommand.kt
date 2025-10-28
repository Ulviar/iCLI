package com.github.ulviar.icli.testing

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a java command line that launches [com.github.ulviar.icli.testing.processes.TestProcess]
 * with optional arguments. Integration tests rely on this helper to ensure the compiled test
 * classes and resources are present on the classpath when the process is started from Gradle.
 */
object TestProcessCommand {
    fun command(vararg args: String): List<String> {
        val javaHome = System.getProperty("java.home")
        val javaExec =
            if (isWindows) {
                Path.of(javaHome, "bin", "java.exe")
            } else {
                Path.of(javaHome, "bin", "java")
            }

        val baseClasspath = System.getProperty("java.class.path")
        val extraEntries =
            listOf(
                Path.of("build", "classes", "kotlin", "test"),
                Path.of("build", "classes", "java", "test"),
                Path.of("build", "resources", "test"),
            ).filter { Files.exists(it) }
                .map { it.toAbsolutePath().toString() }

        val classpath =
            (baseClasspath.split(File.pathSeparator) + extraEntries)
                .distinct()
                .joinToString(File.pathSeparator)

        val mainClass = "com.github.ulviar.icli.testing.processes.TestProcess"

        return buildList {
            add(javaExec.toString())
            add("-cp")
            add(classpath)
            add(mainClass)
            addAll(args.asList())
        }
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
}
