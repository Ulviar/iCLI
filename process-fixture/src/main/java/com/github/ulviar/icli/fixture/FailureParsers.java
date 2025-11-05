package com.github.ulviar.icli.fixture;

import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class FailureParsers {
    private FailureParsers() {}

    static FailurePlan fromSpecification(@Nullable String value) {
        if (value == null || value.isBlank() || "never".equalsIgnoreCase(value)) {
            return FailurePlan.NEVER;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("random")) {
            double probability = extractDouble(lower, "random");
            int exit = extractExitCode(lower, 23);
            return new FailurePlan.RandomFailure(probability, exit);
        }
        if (lower.startsWith("at")) {
            long request = extractLong(lower, "at");
            int exit = extractExitCode(lower, 24);
            return new FailurePlan.AtRequest(request, exit);
        }
        if (lower.startsWith("exit-code")) {
            int exit = (int) extractLong(lower, "exit-code");
            return new FailurePlan.ExitCodeFailure(exit);
        }
        if (lower.startsWith("hang")) {
            long request = lower.contains(":") ? extractLong(lower, "hang") : 1;
            return new FailurePlan.HangFailure(request);
        }
        throw new IllegalArgumentException("Unsupported failure plan: " + value);
    }

    static FailurePlan fromObject(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return FailurePlan.NEVER;
        }
        Object type = map.get("type");
        if (!(type instanceof String typeString)) {
            return FailurePlan.NEVER;
        }
        return fromSpecification(typeString + buildSuffix(map));
    }

    private static String buildSuffix(Map<?, ?> map) {
        Object probability = map.get("probability");
        Object request = map.get("request");
        Object exit = map.get("exitCode");
        StringBuilder builder = new StringBuilder();
        if (probability != null) {
            builder.append(':').append(probability);
        } else if (request != null) {
            builder.append(':').append(request);
        }
        if (exit != null) {
            builder.append(":exit=").append(exit);
        }
        return builder.toString();
    }

    private static double extractDouble(String raw, String prefix) {
        int idx = raw.indexOf(':');
        if (idx < 0) {
            throw new IllegalArgumentException("Missing value for " + prefix);
        }
        String value = raw.substring(idx + 1);
        int extra = value.indexOf(':');
        if (extra >= 0) {
            value = value.substring(0, extra);
        }
        return Double.parseDouble(value);
    }

    private static long extractLong(String raw, String prefix) {
        int idx = raw.indexOf(':');
        if (idx < 0) {
            throw new IllegalArgumentException("Missing value for " + prefix);
        }
        String value = raw.substring(idx + 1);
        int extra = value.indexOf(':');
        if (extra >= 0) {
            value = value.substring(0, extra);
        }
        return Long.parseLong(value);
    }

    private static int extractExitCode(String raw, int defaultValue) {
        int marker = raw.indexOf("exit=");
        if (marker < 0) {
            return defaultValue;
        }
        int end = raw.indexOf(':', marker);
        String value = end > marker ? raw.substring(marker + 5, end) : raw.substring(marker + 5);
        return Integer.parseInt(value);
    }
}
