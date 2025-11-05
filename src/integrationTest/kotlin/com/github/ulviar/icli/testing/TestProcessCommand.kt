package com.github.ulviar.icli.testing

/**
 * Builds a java command line that launches [com.github.ulviar.icli.testing.processes.TestProcess]
 * with optional arguments. Integration tests rely on this helper to ensure the compiled test
 * classes and resources are present on the classpath when the process is started from Gradle.
 */
object TestProcessCommand {
    private const val MAIN_CLASS = "com.github.ulviar.icli.testing.processes.TestProcess"

    fun command(vararg args: String): List<String> = JavaCommandBuilder.command(MAIN_CLASS, *args)
}
