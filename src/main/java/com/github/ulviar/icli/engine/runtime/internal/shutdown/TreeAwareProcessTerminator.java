package com.github.ulviar.icli.engine.runtime.internal.shutdown;

/**
 * {@link ProcessTerminator} implementation that optionally destroys the entire process tree by traversing
 * {@link ProcessHandle#descendants()} before signalling the root. This keeps cancellation semantics consistent across
 * platforms and prevents orphaned grandchildren.
 */
public final class TreeAwareProcessTerminator implements ProcessTerminator {

    @Override
    public void terminate(Process process, boolean destroyTree, boolean force) {
        ProcessHandle handle = process.toHandle();
        if (destroyTree) {
            handle.descendants().forEach(child -> destroy(child, force));
        }
        destroy(handle, force);
    }

    private static void destroy(ProcessHandle handle, boolean force) {
        if (force) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
    }
}
