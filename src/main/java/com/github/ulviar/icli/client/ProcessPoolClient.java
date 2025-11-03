package com.github.ulviar.icli.client;

import com.github.ulviar.icli.engine.ProcessEngine;
import com.github.ulviar.icli.engine.pool.api.ProcessPool;
import com.github.ulviar.icli.engine.pool.api.ProcessPoolConfig;
import java.time.Duration;

/**
 * Essential API facade over {@link ProcessPool}. The client owns the underlying pool lifecycle and exposes helpers for
 * single-request processing as well as conversation-scoped leases while honouring reset hooks, diagnostics, and the
 * configured shutdown policies.
 *
 * <p>Every helper mirrors the behaviour of the lower-level pool:</p>
 * <ul>
 *     <li>{@link #serviceProcessor()} borrows a worker per request and always returns it afterwards, invoking the
 *     supplied {@link ServiceProcessorListener} around each attempt.</li>
 *     <li>{@link #openConversation()} acquires and pins a worker until the caller closes or retires the returned
 *     {@link ServiceConversation}; listener callbacks fire in the documented order.</li>
 *     <li>{@link #close()} shuts the pool down and waits up to the configured drain timeout for active work to finish
 *     before forcing retirement.</li>
 * </ul>
 */
public final class ProcessPoolClient implements AutoCloseable {

    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(30);

    private final ProcessPool pool;
    private final ClientScheduler scheduler;
    private final ResponseDecoder responseDecoder;
    private final ServiceProcessorListener listener;
    private final Duration drainTimeout;

    private ProcessPoolClient(
            ProcessPool pool,
            ClientScheduler scheduler,
            ResponseDecoder responseDecoder,
            ServiceProcessorListener listener,
            Duration drainTimeout) {
        this.pool = pool;
        this.scheduler = scheduler;
        this.responseDecoder = responseDecoder;
        this.listener = listener;
        this.drainTimeout = drainTimeout;
    }

    /**
     * Creates a new client that owns the {@link ProcessPool}. Callers remain responsible for closing the returned
     * instance to release worker resources.
     *
     * @param engine process engine used to launch pooled workers
     * @param config configuration applied to the underlying pool
     * @param scheduler scheduler used for asynchronous helpers
     * @param responseDecoder decoder applied by {@link ServiceProcessor} when handling line-oriented requests
     * @param listener service-level diagnostics listener; invoked synchronously and expected not to throw
     *
     * @return a new client wrapping a freshly created pool
     */
    public static ProcessPoolClient create(
            ProcessEngine engine,
            ProcessPoolConfig config,
            ClientScheduler scheduler,
            ResponseDecoder responseDecoder,
            ServiceProcessorListener listener) {
        ProcessPool pool = ProcessPool.create(engine, config);
        Duration drainTimeout = normalisedDrainTimeout(config.requestTimeout());
        return new ProcessPoolClient(pool, scheduler, responseDecoder, listener, drainTimeout);
    }

    private static Duration normalisedDrainTimeout(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_DRAIN_TIMEOUT;
        }
        return timeout;
    }

    /**
     * Returns a stateless request processor that borrows a worker for each invocation. Every call receives a fresh
     * {@link ServiceProcessor} instance and therefore can be reused across threads.
     *
     * @return new service processor configured with this client’s scheduler, decoder, and listener
     */
    public ServiceProcessor serviceProcessor() {
        return new ServiceProcessor(pool::acquire, scheduler, responseDecoder, listener);
    }

    /**
     * Returns a stateless request processor that decodes responses using the provided {@link ResponseDecoder}.
     *
     * @param decoder decoder applied to responses
     * @return service processor bound to the decoder
     */
    public ServiceProcessor serviceProcessor(ResponseDecoder decoder) {
        return new ServiceProcessor(pool::acquire, scheduler, decoder, listener);
    }

    /**
     * Opens a conversation-scoped lease that keeps a worker checked out until {@link ServiceConversation#close()} is
     * invoked. The call blocks until a worker is available or the pool is unable to serve the request. The caller is
     * responsible for returning the lease by closing or retiring the conversation.
     *
     * @return conversation bound to a single pooled worker
     */
    public ServiceConversation openConversation() {
        return new ServiceConversation(pool.acquire(), responseDecoder, scheduler, listener);
    }

    /**
     * Exposes the underlying {@link ProcessPool} for advanced scenarios. The returned reference remains owned by this
     * client; callers must not close it directly.
     *
     * @return wrapped pool
     */
    public ProcessPool pool() {
        return pool;
    }

    /**
     * Returns a view that shares the underlying pool but uses the supplied listener. Passing the current listener
     * returns this instance.
     *
     * @param listener listener to associate with pooled requests and conversations
     * @return client view bound to the listener
     */
    public ProcessPoolClient withListener(ServiceProcessorListener listener) {
        if (this.listener == listener) {
            return this;
        }
        return new ProcessPoolClient(pool, scheduler, responseDecoder, listener, drainTimeout);
    }

    /**
     * Closes the underlying pool and drains active work. The drain timeout defaults to the request timeout configured
     * on {@link ProcessPoolConfig} (falling back to a 30&nbsp;second ceiling when the request timeout is not positive).
     *
     * <p>The method is idempotent; subsequent invocations have no additional effect. If the pool cannot drain within
     * the configured timeout an {@link IllegalStateException} is thrown so callers can surface the failure.</p>
     */
    @Override
    public void close() {
        pool.close();
        if (!pool.drain(drainTimeout)) {
            throw new IllegalStateException(
                    "Process pool was unable to drain within " + drainTimeout + " (active leases still running)");
        }
    }
}
