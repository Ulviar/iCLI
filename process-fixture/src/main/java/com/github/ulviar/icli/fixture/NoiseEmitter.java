package com.github.ulviar.icli.fixture;

import java.io.PrintStream;

final class NoiseEmitter {
    private final NoiseProfile profile;
    private final PrintStream stderr;
    private final FixtureRandom random;

    NoiseEmitter(NoiseProfile profile, PrintStream stderr, FixtureRandom random) {
        this.profile = profile;
        this.stderr = stderr;
        this.random = random;
    }

    void maybeEmit(String phase, long sequence) {
        int lines;
        switch (profile.stderrLevel()) {
            case QUIET -> {
                return;
            }
            case NORMAL -> lines = 1;
            case LOUD -> lines = 3;
            default -> lines = 0;
        }
        for (int i = 0; i < lines; i++) {
            if (random.nextDouble() < 0.5d) {
                stderr.printf("[fixture-noise] phase=%s sequence=%d line=%d%n", phase, sequence, i + 1);
            }
        }
    }
}
