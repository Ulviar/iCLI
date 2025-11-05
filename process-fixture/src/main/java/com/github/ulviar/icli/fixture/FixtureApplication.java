package com.github.ulviar.icli.fixture;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Runs the process fixture against the provided streams.
 */
public final class FixtureApplication {

    private static final String USAGE = """
            process-fixture flags:
              --mode=<single|line|stream>
              --startup-ms=<millis>
              --runtime-min-ms=<millis>
              --runtime-max-ms=<millis>
              --payload=<text|base64>:<size>
              --streaming=<smooth|burst|chunked>
              --stream-burst-size=<n>
              --stream-burst-interval-ms=<millis>
              --failure=<never|random:prob|at:N|exit-code:M|hang[:N]>
              --seed=<long>
              --log-format=<json|text>
              --stderr-rate=<quiet|normal|loud>
              --echo-env
              --stream-max-chunks=<n>
            Line/stream commands: PING, EXIT, CONFIG {..}, RESET, FAIL <code>, HANG, STOP, PAUSE, RESUME, JSON overrides.
            """;

    public FixtureApplication() {}

    public int run(String[] args, InputStream in, OutputStream out, OutputStream err) {
        PrintStream stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        if (hasFlag(args, "--help")) {
            printUsage(stdout);
            return 0;
        }
        FixtureCliParser parser = new FixtureCliParser();
        FixtureConfig config;
        try {
            config = parser.parse(args);
        } catch (IllegalArgumentException ex) {
            stderr.println("ERROR " + ex.getMessage());
            return 64;
        }
        FixtureRuntime runtime = new FixtureRuntime(config, in, out, err);
        return runtime.execute();
    }

    private static boolean hasFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(flag::equalsIgnoreCase);
    }

    private static void printUsage(PrintStream out) {
        out.print(USAGE);
    }
}
