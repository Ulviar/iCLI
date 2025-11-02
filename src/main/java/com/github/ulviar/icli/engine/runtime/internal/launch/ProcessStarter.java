package com.github.ulviar.icli.engine.runtime.internal.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Abstraction around {@link ProcessBuilder} to ease testing.
 */
public interface ProcessStarter {

    /**
     * Start a process with the given command/environment/working directory.
     *
     * @param commandLine argv list (already merged with shell wrapper if required)
     * @param workingDirectory optional working directory
     * @param environment environment variables to apply on top of the system defaults
     * @return running {@link Process}
     */
    Process start(
            List<String> commandLine,
            @Nullable Path workingDirectory,
            Map<String, String> environment,
            boolean redirectErrorStream);
}
