package com.github.ulviar.icli.engine.runtime.internal.terminal;

import com.github.ulviar.icli.engine.ShutdownSignal;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Terminal controller backed by a {@link PtyProcess}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Controller must retain the live PtyProcess handle to issue resize and signal commands; "
                + "the reference is never exposed outside the class.")
public final class PtyTerminalController implements TerminalController {

    private static final int CTRL_C = 0x03;

    private final PtyProcess process;

    public PtyTerminalController(PtyProcess process) {
        this.process = process;
    }

    @Override
    public void resize(int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return;
        }
        process.setWinSize(new WinSize(columns, rows));
    }

    @Override
    public void send(ShutdownSignal signal) {
        switch (signal) {
            case INTERRUPT -> sendControlCode(CTRL_C);
            case TERMINATE -> process.destroy();
            case KILL -> process.destroyForcibly();
        }
    }

    private void sendControlCode(int code) {
        try {
            OutputStream stdin = process.getOutputStream();
            stdin.write(code);
            stdin.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write control code to PTY", ex);
        }
    }
}
