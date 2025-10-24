package com.github.ulviar.icli.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/** Core handle for interactive sessions. */
public interface InteractiveSession extends AutoCloseable {

    OutputStream stdin();

    InputStream stdout();

    InputStream stderr();

    CompletableFuture<Integer> onExit();

    @Override
    void close();
}
