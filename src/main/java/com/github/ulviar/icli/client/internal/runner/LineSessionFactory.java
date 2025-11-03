package com.github.ulviar.icli.client.internal.runner;

import com.github.ulviar.icli.client.ClientScheduler;
import com.github.ulviar.icli.client.CommandCall;
import com.github.ulviar.icli.client.InteractiveSessionClient;
import com.github.ulviar.icli.client.LineSessionClient;
import com.github.ulviar.icli.client.ResponseDecoder;

/**
 * Helper responsible for creating {@link LineSessionClient} instances backed by a launched interactive session while
 * wiring in the runner's {@link ClientScheduler}.
 *
 * <p>The factory keeps no mutable state; it simply coordinates session launch and wraps the resulting
 * {@link InteractiveSessionClient}. It is therefore safe to reuse across threads.</p>
 */
public final class LineSessionFactory {

    private final ClientScheduler scheduler;

    /**
     * Creates a factory that reuses the provided scheduler for asynchronous helpers on produced clients.
     *
     * @param scheduler scheduler used by {@link LineSessionClient#processAsync(String)} and related helpers
     */
    public LineSessionFactory(ClientScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Launches a session using the provided {@link SessionLauncher} and produces a {@link LineSessionClient}.
     *
     * <p>The resulting client inherits the {@link ResponseDecoder} attached to the {@link CommandCall} and delegates all
     * asynchronous work to the configured scheduler. A typical usage flow is shown below:</p>
     *
     * <pre>{@code
     * LineSessionClient client = factory.open(launcher, call);
     * try {
     *     CommandResult<String> result = client.process("STATUS");
     *     // handle result...
     * } finally {
     *     client.close();
     * }
     * }</pre>
     *
     * @param launcher session launcher responsible for turning the call into a live interactive session
     * @param call command call describing the session to open
     * @return new line session client bound to the launched session
     * @throws UnsupportedOperationException when the launcher cannot satisfy the requested terminal preference
     * @throws RuntimeException when process start-up or IO initialisation fails
     * @see com.github.ulviar.icli.client.LineSessionRunner
     */
    public LineSessionClient open(SessionLauncher launcher, CommandCall call) {
        InteractiveSessionClient session = launcher.launch(call);
        return LineSessionClient.create(session, call.decoder(), scheduler);
    }
}
