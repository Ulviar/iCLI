package com.github.ulviar.icli.client.pooled;

/**
 * Encapsulates how a {@link ServiceConversation} should terminate its worker lease, keeping the invariants surrounding
 * retirement metadata localized to a single value type.
 *
 * <p>The directive has exactly two variants:
 * <ul>
 *     <li>{@link #closeNormally()} — return the worker to the pool without retiring it.</li>
 *     <li>{@link #retire(ConversationRetirement)} — retire the worker and surface the provided metadata.</li>
 * </ul>
 * Callers can freely reuse directives; they are immutable and thread-safe.
 *
 * <p><strong>Usage example</strong></p>
 *
 * <pre>{@code
 * CloseDirective directive = CloseDirective.retire(ConversationRetirement.unhealthy("lost heartbeat"));
 * conversation.close(directive);
 * }</pre>
 */
sealed interface CloseDirective permits CloseDirective.NormalClose, CloseDirective.RetireClose {

    /**
     * Returns a directive instructing the caller to close the conversation normally without retiring the worker.
     *
     * @return shared normal-close directive
     */
    static CloseDirective closeNormally() {
        return NormalClose.INSTANCE;
    }

    /**
     * Returns a directive indicating that the worker must be retired with the supplied metadata.
     *
     * @param retirement descriptor explaining why retirement is requested
     * @return retire directive carrying the metadata
     */
    static CloseDirective retire(ConversationRetirement retirement) {
        return new RetireClose(retirement);
    }

    /**
     * Signals whether the directive requires retirement.
     *
     * @return {@code true} when {@link #retirement()} is meaningful
     */
    boolean retire();

    /**
     * Returns the retirement metadata when {@link #retire()} is {@code true}.
     *
     * @return retirement descriptor
     * @throws IllegalStateException when invoked on {@link #closeNormally()}
     */
    default ConversationRetirement retirement() {
        throw new IllegalStateException("retirement metadata is only available when retire() is true");
    }

    /**
     * Normal-close directive; implemented as a singleton to avoid needless allocations.
     */
    final class NormalClose implements CloseDirective {

        private static final NormalClose INSTANCE = new NormalClose();

        private NormalClose() {}

        @Override
        public boolean retire() {
            return false;
        }
    }

    /**
     * Retire directive carrying the associated {@link ConversationRetirement} metadata.
     *
     * @param retirement descriptor that justifies retirement
     */
    record RetireClose(ConversationRetirement retirement) implements CloseDirective {

        @Override
        public boolean retire() {
            return true;
        }
    }
}
