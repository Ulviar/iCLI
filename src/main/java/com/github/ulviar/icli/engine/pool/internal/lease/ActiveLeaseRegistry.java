package com.github.ulviar.icli.engine.pool.internal.lease;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe registry of currently leased workers keyed by worker identifier. {@link DefaultWorkerLease} instances
 * register their {@link DefaultLeaseScope} on acquisition so diagnostics, timeout schedulers, and drain routines can
 * inspect active leases without contending on the pool’s main lock.
 *
 * <p>The registry provides a weakly consistent view: iterations observe a snapshot at the time the callback executes
 * and may miss concurrent registrations/removals. This behaviour is sufficient for diagnostics and timeout probes,
 * which only require best-effort visibility.
 */
public final class ActiveLeaseRegistry {

    private final ConcurrentMap<Integer, DefaultLeaseScope> activeLeases = new ConcurrentHashMap<>();

    /**
     * Registers the supplied scope as the sole active lease for {@code workerId}, replacing any previous entry. This
     * method is idempotent for the same scope reference.
     *
     * @param workerId identifier of the leased worker
     * @param scope immutable scope describing the active lease
     */
    public void register(int workerId, DefaultLeaseScope scope) {
        activeLeases.put(workerId, scope);
    }

    /**
     * Removes and returns the active lease for {@code workerId}.
     *
     * @param workerId identifier of the worker whose lease should be removed
     * @return the previously registered scope or {@code null} if no lease exists
     */
    @Nullable
    public DefaultLeaseScope remove(int workerId) {
        return activeLeases.remove(workerId);
    }

    /**
     * Iterates over the current registry contents. The callback observes a point-in-time view and should avoid blocking
     * or long-running work to prevent starving concurrent updates.
     *
     * @param consumer callback invoked for each registered lease
     */
    public void forEach(BiConsumer<Integer, DefaultLeaseScope> consumer) {
        activeLeases.forEach(consumer);
    }
}
