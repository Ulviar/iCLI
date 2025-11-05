package com.github.ulviar.icli.fixture;

/**
 * Command-line entry point for the process fixture CLI.
 */
public final class ProcessFixture {

    private ProcessFixture() {}

    public static void main(String[] args) {
        FixtureApplication application = new FixtureApplication();
        int exit = application.run(args, System.in, System.out, System.err);
        System.exit(exit);
    }
}
