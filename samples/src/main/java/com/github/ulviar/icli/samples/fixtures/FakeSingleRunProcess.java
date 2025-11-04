package com.github.ulviar.icli.samples.fixtures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Simple CLI used by tests to validate executor wiring without invoking external tooling.
 */
public final class FakeSingleRunProcess {

    private FakeSingleRunProcess() {}

    public static void main(String[] args) throws Exception {
        FakeSingleRunProcess process = new FakeSingleRunProcess();
        process.run(args);
    }

    private void run(String[] args) throws IOException, InterruptedException {
        String stdout = args.length > 0 ? args[0] : "fake::stdout";
        String stderr = args.length > 1 ? args[1] : "";
        int exitCode = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        long sleepMillis = args.length > 3 ? Long.parseLong(args[3]) : 0L;

        System.out.write(stdout.getBytes(StandardCharsets.UTF_8));
        System.out.flush();

        System.err.write(stderr.getBytes(StandardCharsets.UTF_8));
        System.err.flush();

        if (sleepMillis > 0) {
            Thread.sleep(sleepMillis);
        }

        if (args.length > 4) {
            System.out.write((" args=" + Arrays.toString(args)).getBytes(StandardCharsets.UTF_8));
        }

        System.exit(exitCode);
    }
}
