package com.github.ulviar.icli.core.runtime.launch;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** {@link PtyProcessFactory} backed by pty4j's {@link PtyProcessBuilder}. */
final class Pty4jProcessFactory implements PtyProcessFactory {
    @Override
    public PtyProcess start(PtyLaunchRequest request) throws IOException {
        PtyProcessBuilder builder = new PtyProcessBuilder(request.commandLine().toArray(String[]::new));
        builder.setEnvironment(new LinkedHashMap<>(request.environment()));
        builder.setRedirectErrorStream(request.redirectErrorStream());
        builder.setInitialColumns(request.columns());
        builder.setInitialRows(request.rows());
        builder.setUseWinConPty(request.useWinConPty());
        Path workingDirectory = request.workingDirectory();
        if (workingDirectory != null) {
            builder.setDirectory(workingDirectory.toAbsolutePath().toString());
        }
        return builder.start();
    }
}
