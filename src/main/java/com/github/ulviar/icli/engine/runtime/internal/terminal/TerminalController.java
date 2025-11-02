package com.github.ulviar.icli.engine.runtime.internal.terminal;

import com.github.ulviar.icli.engine.ShutdownSignal;

/**
 * Abstraction over PTY-specific controls (window resizing, signal injection). Pipe-backed sessions rely on the no-op
 * implementation.
 */
public interface TerminalController {

    TerminalController NO_OP = new TerminalController() {
        @Override
        public void resize(int columns, int rows) {
            // nothing to do
        }

        @Override
        public void send(ShutdownSignal signal) {
            // nothing to do
        }
    };

    void resize(int columns, int rows);

    void send(ShutdownSignal signal);
}
