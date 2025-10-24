package com.github.ulviar.icli.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** Reads output until the configured delimiter (default {@code '\n'}) and decodes it as text. */
final class LineDelimitedResponseDecoder implements ResponseDecoder {
    private final int delimiter;

    LineDelimitedResponseDecoder() {
        this('\n');
    }

    LineDelimitedResponseDecoder(int delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public String read(InputStream stdout, Charset charset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int value = stdout.read();
            if (value == -1) {
                throw new IOException("End of stream reached before delimiter");
            }
            if (value == delimiter) {
                break;
            }
            buffer.write(value);
        }
        return buffer.toString(charset);
    }
}
