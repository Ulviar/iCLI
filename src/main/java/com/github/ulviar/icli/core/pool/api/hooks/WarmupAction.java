package com.github.ulviar.icli.core.pool.api.hooks;

import com.github.ulviar.icli.core.InteractiveSession;

/**
 * Optional action executed immediately after a worker launches and before it is offered for leasing. Warm-up actions
 * may perform tasks such as loading frequently used modules, establishing authentication context, or seeding caches.
 * Failures prevent the worker from joining the pool.
 */
@FunctionalInterface
public interface WarmupAction {

    /**
     * Executes the warm-up logic for the supplied session.
     *
     * @param session newly created interactive session
     * @throws Exception when the warm-up fails; the pool closes and retires the worker
     */
    void perform(InteractiveSession session) throws Exception;
}
