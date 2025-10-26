package com.github.ulviar.icli.core.runtime.shutdown;

/**
 * Handles termination of a running process according to the shutdown strategy.
 */
public interface ProcessTerminator {

    /**
     * Terminate the given process.
     *
     * @param process process to terminate
     * @param destroyTree whether child processes should also be terminated
     * @param force whether to send a hard kill ({@code true}) or a graceful signal ({@code false})
     */
    void terminate(Process process, boolean destroyTree, boolean force);
}
