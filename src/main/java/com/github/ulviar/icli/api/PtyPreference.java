package com.github.ulviar.icli.api;

/** Preferred PTY behaviour when launching a command. */
public enum PtyPreference {
    /** Let the runtime decide based on defaults and heuristics. */
    AUTO,
    /** Require a PTY; fail if unavailable. */
    REQUIRED,
    /** Force pipes even if a PTY would normally be used. */
    DISABLED
}
