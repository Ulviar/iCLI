package com.github.ulviar.icli.api;

/** Signal semantics used when attempting graceful command termination. */
public enum TerminationSignal {
    /** Send an interrupt (Ctrl+C / SIGINT) when supported. */
    INTERRUPT,
    /** Send a terminate request (Ctrl+Break / SIGTERM) when supported. */
    TERMINATE,
    /** Do not send any signal before forcefully destroying the process. */
    NONE
}
