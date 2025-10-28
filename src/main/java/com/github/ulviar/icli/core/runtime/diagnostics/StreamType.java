package com.github.ulviar.icli.core.runtime.diagnostics;

/**
 * Identifies which process stream emitted a diagnostics event.
 */
public enum StreamType {
    /** Standard output stream. */
    STDOUT,

    /** Standard error stream. */
    STDERR,

    /** Stream with stderr merged into stdout; emitted when merge mode is enabled. */
    MERGED
}
