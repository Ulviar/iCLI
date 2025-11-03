package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.pool.api.LeaseScope;
import com.github.ulviar.icli.engine.pool.api.WorkerLease;
import com.github.ulviar.icli.engine.pool.api.hooks.ResetRequest;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Stateless helper that borrows a pooled worker for a single line-oriented request.
 *
 * <p>Instances are thread-safe and reusable. Each invocation acquires a fresh {@link WorkerLease}, issues the request
 * via {@link LineSessionClient}, and always returns the worker to the pool once the listener and reset hooks complete.
 * Callers supply a {@link ServiceProcessorListener} to observe success and failure events; listener implementations are
 * expected to avoid throwing, but if they do the exception is propagated after the worker has been reset.</p>
 */
public final class ServiceProcessor {

    private final Supplier<WorkerLease> leaseSupplier;
    private final ClientScheduler scheduler;
    private final ResponseDecoder decoder;
    private final ServiceProcessorListener listener;

    ServiceProcessor(
            Supplier<WorkerLease> leaseSupplier,
            ClientScheduler scheduler,
            ResponseDecoder decoder,
            ServiceProcessorListener listener) {
        this.leaseSupplier = leaseSupplier;
        this.scheduler = scheduler;
        this.decoder = decoder;
        this.listener = listener;
    }

    /**
     * Processes a single request using a pooled worker.
     *
     * <p>The method acquires a lease, dispatches {@code input} to the underlying {@link LineSessionClient}, and returns
     * the resulting {@link CommandResult}. Regardless of the outcome the lease is returned to the pool; on failures the
     * processor performs a manual reset, notifies the {@link ServiceProcessorListener}, and then rethrows the original
     * error so callers observe the same failure they would see when working with the underlying session directly.</p>
     *
     * @param input payload forwarded to the pooled worker
     * @return command result describing success or failure
     */
    public CommandResult<String> process(String input) {
        WorkerLease lease = leaseSupplier.get();
        LeaseScope scope = lease.scope();
        listener.requestStarted(scope, input);
        InteractiveSessionClient sessionClient = InteractiveSessionClient.wrap(lease.session());
        LineSessionClient client = LineSessionClient.create(sessionClient, decoder, scheduler);
        try (lease) {
            CommandResult<String> result = client.process(input);
            if (result.success()) {
                listener.requestCompleted(scope, result);
            } else {
                lease.reset(ResetRequest.manual(scope.requestId()));
                listener.requestFailed(scope, result.error());
            }
            return result;
        } catch (Throwable ex) {
            lease.reset(ResetRequest.manual(scope.requestId()));
            listener.requestFailed(scope, ex);
            throw ex;
        }
    }

    /**
     * Asynchronously invokes {@link #process(String)} on the configured {@link ClientScheduler}.
     *
     * <p>The returned future completes with the same {@link CommandResult} as {@link #process(String)} or completes
     * exceptionally with the original failure after the lease reset and listener callbacks finish.</p>
     *
     * @param input payload forwarded to the pooled worker
     *
     * @return future completed with the result of {@link #process(String)}
     */
    public CompletableFuture<CommandResult<String>> processAsync(String input) {
        return scheduler.submit(() -> process(input));
    }
}
