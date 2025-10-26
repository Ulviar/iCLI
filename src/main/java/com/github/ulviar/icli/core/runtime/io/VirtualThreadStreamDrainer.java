package com.github.ulviar.icli.core.runtime.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link StreamDrainer} backed by virtual threads so stdout/stderr pumps never block the main execution thread or starve
 * other work. Each drain operation owns its own virtual thread and completes a
 * {@link java.util.concurrent.CompletableFuture} once EOF is observed or an I/O error occurs.
 */
public final class VirtualThreadStreamDrainer implements StreamDrainer, AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public CompletableFuture<Void> drain(InputStream source, OutputSink sink) {
        return CompletableFuture.runAsync(
                () -> {
                    try (InputStream in = source) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            sink.append(buffer, 0, read);
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                },
                executor);
    }

    @Override
    public void close() {
        executor.close();
    }
}
