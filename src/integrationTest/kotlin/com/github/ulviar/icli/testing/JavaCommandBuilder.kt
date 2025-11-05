package com.github.ulviar.icli.testing

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds `java` command lines that mirror the Gradle test classpath while adding the integration/test build outputs.
 */
object JavaCommandBuilder {
    fun command(
        mainClass: String,
        vararg args: String,
    ): List<String> {
        val javaHome = System.getProperty("java.home")
        val javaExec =
            if (isWindows) {
                Path.of(javaHome, "bin", "java.exe")
            } else {
                Path.of(javaHome, "bin", "java")
            }

        val baseClasspath = System.getProperty("java.class.path")
        val extraEntries = additionalClasspathEntries()
        val classpath =
            (baseClasspath.split(File.pathSeparator) + extraEntries)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(File.pathSeparator)

        return buildList {
            add(javaExec.toString())
            add("-cp")
            add(classpath)
            add(mainClass)
            addAll(args.asList())
        }
    }

    private fun additionalClasspathEntries(): List<String> =
        listOf(
            Path.of("build", "classes", "kotlin", "test"),
            Path.of("build", "classes", "java", "test"),
            Path.of("build", "resources", "test"),
            Path.of("build", "classes", "kotlin", "integrationTest"),
            Path.of("build", "classes", "java", "integrationTest"),
            Path.of("build", "resources", "integrationTest"),
            Path.of("process-fixture", "build", "classes", "java", "main"),
            Path.of("process-fixture", "build", "classes", "kotlin", "main"),
            Path.of("process-fixture", "build", "resources", "main"),
        ).filter { Files.exists(it) }
            .map { it.toAbsolutePath().toString() }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
}
