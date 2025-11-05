package com.github.ulviar.icli.fixture;

import java.util.Base64;
import org.jetbrains.annotations.Nullable;

final class PayloadGenerator {
    private static final char[] TEXT_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private PayloadGenerator() {}

    static FixturePayload generate(
            PayloadProfile profile, FixtureRandom random, long requestId, @Nullable String label) {
        if (profile.format() == PayloadFormat.TEXT) {
            StringBuilder builder = new StringBuilder(profile.size());
            for (int i = 0; i < profile.size(); i++) {
                int idx = random.nextInt(TEXT_ALPHABET.length);
                builder.append(TEXT_ALPHABET[idx]);
            }
            if (label != null && !label.isEmpty()) {
                builder.append('-').append(label);
            } else {
                builder.append('-').append(requestId);
            }
            return new FixturePayload(builder.toString(), builder.length());
        }
        byte[] bytes = new byte[Math.max(profile.size(), 1)];
        random.fill(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return new FixturePayload(encoded, encoded.length());
    }
}
