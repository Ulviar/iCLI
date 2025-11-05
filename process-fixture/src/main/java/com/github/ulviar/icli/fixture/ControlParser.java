package com.github.ulviar.icli.fixture;

import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class ControlParser {
    private ControlParser() {}

    static RequestOverrides parseRequest(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        RuntimeBounds runtime = runtimeFrom(root);
        PayloadProfile payload = payloadFrom(root.get("payload"));
        FailurePlan failure = failureFrom(root.get("fail"));
        FixtureMode mode = modeFrom(root.get("mode"));
        Integer chunkCount = toInteger(root.get("chunks"));
        String label = toString(root.get("label"));
        StreamingStyle streamingStyle = streamingStyle(root.get("streaming"));
        if (runtime == null
                && payload == null
                && failure == null
                && mode == null
                && chunkCount == null
                && label == null
                && streamingStyle == null) {
            throw new IllegalArgumentException("Request override did not specify any fields");
        }
        return new RequestOverrides(runtime, payload, failure, mode, chunkCount, label, streamingStyle);
    }

    static SessionDelta parseConfig(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        RuntimeBounds runtime = runtimeFrom(root);
        PayloadProfile payload = payloadFrom(root.get("payload"));
        StreamingProfile streamingProfile = streamingProfile(root.get("streaming"));
        FailurePlan failure = failureFrom(root.get("fail"));
        if (runtime == null && payload == null && streamingProfile == null && failure == null) {
            throw new IllegalArgumentException("CONFIG override must provide at least one field");
        }
        return new SessionDelta(runtime, payload, streamingProfile, failure);
    }

    private static @Nullable RuntimeBounds runtimeFrom(Map<String, Object> root) {
        Long runtimeMs = toLong(root.get("runtimeMs"));
        Long min = toLong(root.get("runtimeMinMs"));
        Long max = toLong(root.get("runtimeMaxMs"));
        if (runtimeMs != null) {
            return new RuntimeBounds(runtimeMs, runtimeMs);
        }
        if (min == null && max == null) {
            return null;
        }
        if (min == null || max == null) {
            long fallback = min != null ? min : max;
            return new RuntimeBounds(fallback, fallback);
        }
        return new RuntimeBounds(min, max);
    }

    private static @Nullable PayloadProfile payloadFrom(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object type = map.get("type");
        Object size = map.get("size");
        if (!(type instanceof String typeString) || !(size instanceof Number number)) {
            throw new IllegalArgumentException("payload requires type + size");
        }
        PayloadFormat format =
                switch (typeString.toLowerCase(Locale.ROOT)) {
                    case "text" -> PayloadFormat.TEXT;
                    case "bytes", "base64" -> PayloadFormat.BASE64;
                    default -> throw new IllegalArgumentException("Unsupported payload type: " + typeString);
                };
        return new PayloadProfile(format, number.intValue());
    }

    private static @Nullable FailurePlan failureFrom(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return FailureParsers.fromSpecification(string);
        }
        return FailureParsers.fromObject(value);
    }

    private static @Nullable FixtureMode modeFrom(@Nullable Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        return FixtureMode.fromFlag(string);
    }

    private static @Nullable Integer toInteger(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return null;
    }

    private static @Nullable Long toLong(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private static @Nullable String toString(@Nullable Object value) {
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        return null;
    }

    private static @Nullable StreamingStyle streamingStyle(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return StreamingStyle.valueOf(string.toUpperCase(Locale.ROOT));
        }
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type instanceof String stringType) {
                return StreamingStyle.valueOf(stringType.toUpperCase(Locale.ROOT));
            }
        }
        return null;
    }

    private static @Nullable StreamingProfile streamingProfile(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object type = map.get("type");
        Object interval = map.get("intervalMs");
        Object burst = map.get("burstSize");
        if (!(type instanceof String stringType)) {
            throw new IllegalArgumentException("streaming requires type");
        }
        StreamingStyle style = StreamingStyle.valueOf(stringType.toUpperCase(Locale.ROOT));
        long intervalMs = interval != null ? Long.parseLong(String.valueOf(interval)) : 250L;
        int burstSize = burst != null ? Integer.parseInt(String.valueOf(burst)) : 3;
        return new StreamingProfile(style, intervalMs, burstSize);
    }
}
