package com.github.ulviar.icli.fixture;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

final class CommandPump implements AutoCloseable {
    private static final String EOF = "__EOF__";

    private final BufferedReader reader;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    CommandPump(BufferedReader reader) {
        this.reader = reader;
    }

    void start() {
        Thread thread = Thread.ofVirtual().name("fixture-command-pump").start(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    queue.offer(line);
                }
            } catch (IOException ignored) {
            } finally {
                queue.offer(EOF);
            }
        });
    }

    @Nullable
    String poll(long timeoutMs) {
        try {
            String value = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (EOF.equals(value)) {
                running = false;
                return null;
            }
            return value;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void close() {
        running = false;
    }
}
