package com.github.ulviar.icli.client.pooled;

import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that remembers the most recent worker id associated with an affinity key so future requests can reuse that
 * worker if it is still idle.
 */
sealed interface ConversationAffinityRegistry
        permits ConversationAffinityRegistry.EnabledRegistry, ConversationAffinityRegistry.DisabledRegistry {

    /**
     * Returns a registry backed by a thread-safe map.
     *
     * @return enabled registry instance
     */
    static ConversationAffinityRegistry enabled() {
        return new EnabledRegistry();
    }

    /**
     * Returns a singleton registry that never records affinity information.
     *
     * @return disabled registry singleton
     */
    static ConversationAffinityRegistry disabled() {
        return DisabledRegistry.INSTANCE;
    }

    /**
     * Removes and returns a reserved worker id for the provided affinity (if any). The removal is atomic, ensuring the
     * worker id is consumed exactly once per reservation request.
     */
    OptionalInt reserve(ConversationAffinity affinity);

    /**
     * Stores the association between an affinity key and the worker that just satisfied it so that subsequent calls to
     * {@link #reserve(ConversationAffinity)} can attempt to reuse the worker.
     */
    void remember(ConversationAffinity affinity, int workerId);

    /**
     * Removes the association for the provided affinity, typically because the worker retired or the client explicitly
     * ended the session.
     */
    void forget(ConversationAffinity affinity);

    /**
     * Enabled registry that tracks worker ids in a {@link ConcurrentHashMap}.
     */
    final class EnabledRegistry implements ConversationAffinityRegistry {

        private final ConcurrentHashMap<String, Integer> workerByKey = new ConcurrentHashMap<>();

        EnabledRegistry() {}

        @Override
        public OptionalInt reserve(ConversationAffinity affinity) {
            if (!affinity.isPresent()) {
                return OptionalInt.empty();
            }
            Integer workerId = workerByKey.remove(affinity.key());
            if (workerId == null) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(workerId);
        }

        @Override
        public void remember(ConversationAffinity affinity, int workerId) {
            if (!affinity.isPresent()) {
                return;
            }
            workerByKey.put(affinity.key(), workerId);
        }

        @Override
        public void forget(ConversationAffinity affinity) {
            if (!affinity.isPresent()) {
                return;
            }
            workerByKey.remove(affinity.key());
        }
    }

    /**
     * Disabled registry that short-circuits every operation.
     */
    final class DisabledRegistry implements ConversationAffinityRegistry {

        private static final DisabledRegistry INSTANCE = new DisabledRegistry();

        private DisabledRegistry() {}

        @Override
        public OptionalInt reserve(ConversationAffinity affinity) {
            return OptionalInt.empty();
        }

        @Override
        public void remember(ConversationAffinity affinity, int workerId) {
            // no-op
        }

        @Override
        public void forget(ConversationAffinity affinity) {
            // no-op
        }
    }
}
