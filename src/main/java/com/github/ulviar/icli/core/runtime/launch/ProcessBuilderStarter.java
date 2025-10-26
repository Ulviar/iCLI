package com.github.ulviar.icli.core.runtime.launch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ProcessStarter} backed by {@link ProcessBuilder}. Centralising the wiring keeps environment/working-directory
 * handling and error reporting consistent between tests and production code.
 */
public final class ProcessBuilderStarter implements ProcessStarter {

    @Override
    public Process start(
            List<String> commandLine,
            @Nullable Path workingDirectory,
            Map<String, String> environment,
            boolean redirectErrorStream) {
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        builder.environment().putAll(environment);
        builder.redirectErrorStream(redirectErrorStream);
        try {
            return builder.start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to start process: " + String.join(" ", commandLine), ex);
        }
    }
}
