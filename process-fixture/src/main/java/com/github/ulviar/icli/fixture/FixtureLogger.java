package com.github.ulviar.icli.fixture;

import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class FixtureLogger {
    private final LogFormat format;
    private final PrintStream stdout;

    FixtureLogger(LogFormat format, PrintStream stdout) {
        this.format = format;
        this.stdout = stdout;
    }

    void startup(FixtureConfig config) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("mode", config.mode().name().toLowerCase());
        fields.put("startupMs", config.startupDelayMillis());
        fields.put("runtime", describeRuntime(config.runtimeBounds()));
        fields.put("payload", describePayload(config.payloadProfile()));
        fields.put("timestamp", Instant.now().toEpochMilli());
        emit("startup", fields);
    }

    void ready(String type, Map<String, Object> snapshot) {
        emit(type, snapshot);
    }

    void requestStart(long requestId, long runtimeMs, PayloadProfile payload, @Nullable String label) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("runtimeMs", runtimeMs);
        fields.put("payload", describePayload(payload));
        if (label != null) {
            fields.put("label", label);
        }
        emit("request-start", fields);
    }

    void requestComplete(long requestId, String status, int exitCode, long durationMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("status", status);
        fields.put("exitCode", exitCode);
        fields.put("durationMs", durationMs);
        emit("request-complete", fields);
    }

    void lineResponse(long requestId, @Nullable String label, int size) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("label", label);
        fields.put("size", size);
        emit("line-response", fields);
    }

    void chunk(long chunkIndex, int size, String profile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chunk", chunkIndex);
        fields.put("size", size);
        fields.put("profile", profile);
        emit("chunk", fields);
    }

    void streamComplete(long emittedChunks, String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chunks", emittedChunks);
        fields.put("reason", reason);
        emit("stream-complete", fields);
    }

    private static Map<String, Object> describeRuntime(RuntimeBounds bounds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minMs", bounds.minMillis());
        map.put("maxMs", bounds.maxMillis());
        return map;
    }

    private static Map<String, Object> describePayload(PayloadProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("format", profile.format().name().toLowerCase());
        map.put("size", profile.size());
        return map;
    }

    private void emit(String event, Map<String, Object> fields) {
        if (format == LogFormat.JSON) {
            stdout.println(toJson(event, fields));
        } else {
            stdout.println(toText(event, fields));
        }
    }

    private static String toText(String event, Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append(event.toUpperCase()).append(' ');
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(' ');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String toJson(String event, Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"event\":\"").append(event).append('\"');
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            builder.append(',');
            builder.append('"').append(entry.getKey()).append('"').append(':');
            appendValue(builder, entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder builder, Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(entry.getKey()).append('"').append(':');
                appendValue(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value == null) {
            builder.append("null");
        } else {
            builder.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
