package com.github.ulviar.icli.fixture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

final class FixtureIO {
    private final BufferedReader reader;
    private final PrintStream stdout;
    private final PrintStream stderr;
    private final OutputStream rawOut;

    private FixtureIO(BufferedReader reader, PrintStream stdout, PrintStream stderr, OutputStream rawOut) {
        this.reader = reader;
        this.stdout = stdout;
        this.stderr = stderr;
        this.rawOut = rawOut;
    }

    static FixtureIO create(InputStream in, OutputStream out, OutputStream err) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        PrintStream stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        return new FixtureIO(reader, stdout, stderr, out);
    }

    BufferedReader reader() {
        return reader;
    }

    PrintStream stdout() {
        return stdout;
    }

    PrintStream stderr() {
        return stderr;
    }

    OutputStream rawOut() {
        return rawOut;
    }

    void closeQuietly() {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
        stdout.flush();
        stderr.flush();
    }
}
