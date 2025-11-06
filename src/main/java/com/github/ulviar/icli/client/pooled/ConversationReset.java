package com.github.ulviar.icli.client.pooled;

/**
 * Immutable descriptor explaining why client code triggered a manual reset during a pooled conversation.
 *
 * <p>Reset descriptors surface in diagnostics, audit logs, and listeners such as {@code ResetHook}. They carry
 * human-readable summaries only; no structured fields are stored.</p>
 *
 * <p><strong>Usage example</strong></p>
 *
 * <pre>{@code
 * ConversationReset manualReset = ConversationReset.manual("tenant requested a clean shell");
 * poolClient.reset(manualReset);
 * }</pre>
 *
 * @param reason trimmed, human-readable reason describing the reset trigger
 *
 * @implSpec Invariants:
 *     <ul>
 *         <li>The stored reason is trimmed and non-blank.</li>
 *         <li>Instances are immutable value objects suitable for logging and diagnostics.</li>
 *     </ul>
 */
public record ConversationReset(String reason) {

    private static final String DEFAULT_REASON = "manual reset";

    public ConversationReset {
        reason = normalise(reason);
    }

    /**
     * Returns a reset descriptor with the default {@code "manual reset"} reason for cases where no additional
     * context is available.
     *
     * @return default reset descriptor
     */
    public static ConversationReset manual() {
        return new ConversationReset(DEFAULT_REASON);
    }

    /**
     * Returns a reset descriptor carrying a custom reason string suitable for diagnostics.
     *
     * @param reason description of why the reset occurred; leading and trailing whitespace is ignored
     * @return reset descriptor
     * @throws IllegalArgumentException if {@code reason} is blank after trimming
     */
    public static ConversationReset manual(String reason) {
        return new ConversationReset(reason);
    }

    private static String normalise(String value) {
        String normalised = value.trim();
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("reset reason must not be blank");
        }
        return normalised;
    }
}
