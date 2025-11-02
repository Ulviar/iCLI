package com.github.ulviar.icli.engine.runtime.internal.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Immutable value describing a PTY launch request. */
record PtyLaunchRequest(
        List<String> commandLine,
        Map<String, String> environment,
        boolean redirectErrorStream,
        @Nullable Path workingDirectory,
        int columns,
        int rows,
        boolean useWinConPty) {

    PtyLaunchRequest {
        commandLine = List.copyOf(commandLine);
        environment = Map.copyOf(environment);
    }
}
