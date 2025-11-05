package com.github.ulviar.icli.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

final class FixtureRuntime {
    private final FixtureConfig config;
    private final InputStream in;
    private final OutputStream out;
    private final OutputStream err;

    FixtureRuntime(FixtureConfig config, InputStream in, OutputStream out, OutputStream err) {
        this.config = config;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    int execute() {
        FixtureIO io = FixtureIO.create(in, out, err);
        FixtureRandom random = new FixtureRandom(config.seed());
        FixtureLogger logger = new FixtureLogger(config.logFormat(), io.stdout());
        NoiseEmitter noiseEmitter = new NoiseEmitter(config.noiseProfile(), io.stderr(), random);
        if (config.echoEnvironment()) {
            echoEnvironment(io.stdout());
        }
        try {
            return switch (config.mode()) {
                case SINGLE -> new SingleRunController(config, logger, random, io, noiseEmitter).run();
                case LINE -> new LineSessionController(config, logger, random, io, noiseEmitter).run();
                case STREAM -> new StreamingController(config, logger, random, io, noiseEmitter).run();
            };
        } catch (IOException ioException) {
            io.stderr().println("ERROR " + ioException.getMessage());
            return 1;
        }
    }

    private static void echoEnvironment(PrintStream stdout) {
        System.getenv().forEach((key, value) -> stdout.printf("ENV %s=%s%n", key, value));
    }
}
