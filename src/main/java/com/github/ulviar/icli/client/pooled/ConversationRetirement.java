package com.github.ulviar.icli.client.pooled;

/**
 * Describes why a conversation permanently retired its worker instead of releasing it back to the pool.
 *
 * <p>Retirements are reported to diagnostics listeners and surfaced in logs to explain why capacity shrank. Each
 * descriptor captures a high-level {@link Kind} plus a caller-provided reason.</p>
 *
 * <p><strong>Usage example</strong></p>
 *
 * <pre>{@code poolClient.retire(ConversationRetirement.unhealthy("worker lost agent connection"));
 * }</pre>
 *
 * @param reason trimmed, human-readable description of the retirement trigger
 * @param kind the retirement category describing why the worker should be removed from the pool
 *
 * @implSpec Invariants:
 *     <ul>
 *         <li>Every descriptor has a {@link Kind} describing the category plus a trimmed, non-blank reason.</li>
 *         <li>{@link #unspecified()} is equivalent to {@link Kind#CLIENT_REQUEST} with a canned fallback reason.</li>
 *     </ul>
 */
public record ConversationRetirement(Kind kind, String reason) {

    /**
     * Categorises retirement triggers.
     */
    public enum Kind {
        /** The client explicitly requested retirement due to unhealthy state. */
        UNHEALTHY,

        /** The client no longer needs the worker and is rotating it proactively. */
        CLIENT_REQUEST
    }

    private static final String DEFAULT_REASON = "client requested retirement";

    public ConversationRetirement {
        reason = normalise(reason);
    }

    /**
     * Returns a descriptor indicating the worker became unhealthy and must not be reused.
     *
     * @param reason human-readable description of the health issue; leading and trailing whitespace is ignored
     * @return retirement descriptor
     * @throws IllegalArgumentException if {@code reason} is blank after trimming
     */
    public static ConversationRetirement unhealthy(String reason) {
        return new ConversationRetirement(Kind.UNHEALTHY, reason);
    }

    /**
     * Returns a descriptor representing a proactive rotation requested by the client even though the worker is healthy.
     *
     * @param reason human-readable description; leading and trailing whitespace is ignored
     * @return retirement descriptor
     * @throws IllegalArgumentException if {@code reason} is blank after trimming
     */
    public static ConversationRetirement clientRequest(String reason) {
        return new ConversationRetirement(Kind.CLIENT_REQUEST, reason);
    }

    /**
     * Returns the default retirement descriptor equivalent to {@link Kind#CLIENT_REQUEST} with a canned reason.
     *
     * @return default descriptor
     */
    public static ConversationRetirement unspecified() {
        return new ConversationRetirement(Kind.CLIENT_REQUEST, DEFAULT_REASON);
    }

    /**
     * Returns the retirement category describing why the worker should be removed from the pool.
     *
     * @return retirement kind
     */
    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the trimmed, human-readable description of the retirement trigger.
     *
     * @return retirement reason
     */
    @Override
    public String reason() {
        return reason;
    }

    private static String normalise(String reason) {
        String normalised = reason.trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("retirement reason must not be blank");
        }
        return normalised;
    }
}
