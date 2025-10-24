package com.github.ulviar.icli.client;

/**
 * Describes a non-zero exit while running a command through the {@link CommandService} facade.
 */
public final class ProcessExecutionException extends RuntimeException {
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public ProcessExecutionException(int exitCode, String stdout, String stderr) {
        super("Command exited with " + exitCode);
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}
