package com.github.ulviar.icli.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** Strategy that decodes a textual response from an interactive session's stdout stream. */
@FunctionalInterface
public interface ResponseDecoder {

    String read(InputStream stdout, Charset charset) throws IOException;
}
