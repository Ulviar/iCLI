package com.github.ulviar.icli.core.runtime.diagnostics;

/**
 * Listener notified of diagnostics events emitted while a process is running.
 *
 * <p>Implementations should avoid long blocking operations because events are dispatched from the stream draining
 * threads.
 */
@FunctionalInterface
public interface DiagnosticsListener {

    DiagnosticsListener NO_OP = _ -> {};

    /**
     * Receives the next diagnostics event emitted by the runtime.
     *
     * @param event diagnostics event to process
     */
    void onEvent(DiagnosticsEvent event);

    /**
     * @return listener that ignores every event.
     */
    static DiagnosticsListener noOp() {
        return NO_OP;
    }

    /**
     * Compose this listener with another one. Events are delivered to {@code this} and subsequently to {@code next}.
     *
     * @param next listener to invoke after this listener
     * @return composed listener
     */
    default DiagnosticsListener andThen(DiagnosticsListener next) {
        return event -> {
            onEvent(event);
            next.onEvent(event);
        };
    }
}
