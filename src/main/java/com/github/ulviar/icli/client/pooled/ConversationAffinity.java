package com.github.ulviar.icli.client.pooled;

/**
 * Describes whether a pooled conversation should stick to a previously used worker, enabling lightweight state
 * affinity without pinning resources permanently.
 *
 * <p>Callers construct explicit affinities via {@link #key(String)} and disable stickiness via {@link #none()}. The
 * type is immutable and thread-safe; it may be freely cached, reused across requests, or shared between threads.</p>
 *
 * <p><strong>Usage example</strong></p>
 *
 * <pre>{@code
 * ConversationAffinity affinity = ConversationAffinity.key("tenant-42");
 * if (affinity.isPresent()) {
 *     poolClient.serviceProcessor().process("noop", affinity);
 * }
 * }</pre>
 *
 * @apiNote Provide stable identifiers such as tenant or conversation ids. Random identifiers drastically reduce cache
 * hit rates and negate the benefit of affinity.
 *
 * @implSpec Invariants:
 *     <ul>
 *         <li>The affinity is either {@link #none()} or a {@link #key(String)} with a trimmed, non-blank
 *         identifier.</li>
 *         <li>{@link #key()} may only be invoked when {@link #isPresent()} returns {@code true}.</li>
 *         <li>Instances are immutable; sentinel state is tracked via the {@code present} flag, never via
 *         {@code null}.</li>
 *     </ul>
 *
 * @see ConversationAffinityRegistry
 */
public final class ConversationAffinity {

    private static final ConversationAffinity NONE = new ConversationAffinity(false, "");

    private final boolean present;
    private final String key;

    private ConversationAffinity(boolean present, String key) {
        this.present = present;
        this.key = key;
    }

    /**
     * Creates an affinity tied to the provided identifier so that subsequent leases preferentially reuse the same
     * worker.
     *
     * @param value stable identifier for the affinity scope; leading and trailing whitespace is ignored
     * @return affinity bound to the identifier
     * @throws IllegalArgumentException if {@code value} is blank after trimming
     */
    public static ConversationAffinity key(String value) {
        String normalised = value.trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("affinity key must not be blank");
        }
        return new ConversationAffinity(true, normalised);
    }

    /**
     * Returns an affinity that disables worker stickiness and always yields freshly assigned workers.
     *
     * @return sentinel affinity that represents “no affinity”
     */
    public static ConversationAffinity none() {
        return NONE;
    }

    /**
     * Returns the affinity key, which is guaranteed to be trimmed and non-blank.
     *
     * @return affinity key
     * @throws IllegalStateException when invoked on {@link #none()}
     */
    public String key() {
        if (!present) {
            throw new IllegalStateException("affinity key is not present");
        }
        return key;
    }

    /**
     * Reports whether this affinity carries a key.
     *
     * @return {@code true} when {@link #key()} can be called safely
     */
    boolean isPresent() {
        return present;
    }

    /**
     * Returns a diagnostic-friendly representation indicating whether the affinity is set.
     */
    @Override
    public String toString() {
        return present ? "ConversationAffinity[" + key + "]" : "ConversationAffinity[none]";
    }
}
