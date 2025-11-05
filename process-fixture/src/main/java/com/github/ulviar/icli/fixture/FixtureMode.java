package com.github.ulviar.icli.fixture;

/**
 * Execution modes supported by the fixture.
 */
public enum FixtureMode {
    SINGLE,
    LINE,
    STREAM;

    public static FixtureMode fromFlag(String value) {
        return FixtureMode.valueOf(value.trim().toUpperCase());
    }
}
