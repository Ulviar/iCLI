package com.github.ulviar.icli.engine;

/** Signals used when attempting graceful termination before forcible kill. */
public enum ShutdownSignal {
    INTERRUPT,
    TERMINATE,
    KILL
}
