package com.github.ulviar.icli.testing

/** Builds the java command line for launching the standalone process fixture CLI. */
object ProcessFixtureCommand {
    private const val MAIN_CLASS = "com.github.ulviar.icli.fixture.ProcessFixture"

    fun command(vararg args: String): List<String> = JavaCommandBuilder.command(MAIN_CLASS, *args)
}
