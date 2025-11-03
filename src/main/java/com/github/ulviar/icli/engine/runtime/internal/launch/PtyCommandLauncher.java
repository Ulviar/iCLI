package com.github.ulviar.icli.engine.runtime.internal.launch;

import com.github.ulviar.icli.engine.CommandDefinition;
import com.github.ulviar.icli.engine.TerminalPreference;
import com.github.ulviar.icli.engine.runtime.internal.terminal.PtyTerminalController;
import com.github.ulviar.icli.engine.runtime.internal.terminal.TerminalController;
import com.pty4j.PtyProcess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** {@link CommandLauncher} implementation that provisions PTY-backed processes via pty4j. */
public final class PtyCommandLauncher implements CommandLauncher {

    private static final int DEFAULT_COLUMNS = 120;
    private static final int DEFAULT_ROWS = 32;
    private static final String DEFAULT_TERM = "xterm-256color";
    private final PtyProcessFactory processFactory;
    private final Function<PtyProcess, TerminalController> terminalControllerFactory;

    public PtyCommandLauncher() {
        this(new Pty4jProcessFactory(), PtyTerminalController::new);
    }

    PtyCommandLauncher(
            PtyProcessFactory processFactory, Function<PtyProcess, TerminalController> terminalControllerFactory) {
        this.processFactory = processFactory;
        this.terminalControllerFactory = terminalControllerFactory;
    }

    @Override
    public LaunchedProcess launch(CommandDefinition spec, boolean redirectErrorStream) {
        if (spec.terminalPreference() == TerminalPreference.DISABLED) {
            throw new UnsupportedOperationException("PTY launch disabled for this command.");
        }

        List<String> commandLine = CommandLineBuilder.compose(spec);
        PtyLaunchRequest request = new PtyLaunchRequest(
                commandLine,
                prepareEnvironment(spec.environment()),
                redirectErrorStream,
                spec.workingDirectory(),
                DEFAULT_COLUMNS,
                DEFAULT_ROWS,
                isWindows());
        try {
            PtyProcess process = processFactory.start(request);
            TerminalController controller = terminalControllerFactory.apply(process);
            return new LaunchedProcess(process, commandLine, controller);
        } catch (UnsupportedOperationException | UnsatisfiedLinkError ex) {
            throw new UnsupportedOperationException("PTY is not available on this platform", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to start PTY process: " + String.join(" ", commandLine), ex);
        }
    }

    private static Map<String, String> prepareEnvironment(Map<String, String> overrides) {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.putAll(overrides);
        env.putIfAbsent("TERM", DEFAULT_TERM);
        return env;
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
