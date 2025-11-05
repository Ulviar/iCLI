package com.github.ulviar.icli.fixture;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses CLI arguments into {@link FixtureConfig}.
 */
public final class FixtureCliParser {

    private static final long DEFAULT_STARTUP_MS = 0L;
    private static final long DEFAULT_RUNTIME_MIN_MS = 25L;
    private static final long DEFAULT_RUNTIME_MAX_MS = 250L;
    private static final int DEFAULT_PAYLOAD_SIZE = 64;
    private static final StreamingProfile DEFAULT_STREAMING = new StreamingProfile(StreamingStyle.SMOOTH, 250L, 3);
    private static final long DEFAULT_STREAM_MAX_CHUNKS = Long.MAX_VALUE;

    public FixtureConfig parse(String[] args) {
        Map<String, String> options = parseOptions(args);

        FixtureMode mode = parseMode(options.getOrDefault("mode", "single"));
        long startupMs =
                parseLong(options.getOrDefault("startup-ms", String.valueOf(DEFAULT_STARTUP_MS)), "startup-ms");
        long runtimeMin = parseLong(
                options.getOrDefault("runtime-min-ms", String.valueOf(DEFAULT_RUNTIME_MIN_MS)), "runtime-min-ms");
        long runtimeMax = parseLong(
                options.getOrDefault("runtime-max-ms", String.valueOf(DEFAULT_RUNTIME_MAX_MS)), "runtime-max-ms");
        RuntimeBounds bounds = new RuntimeBounds(runtimeMin, runtimeMax);

        PayloadProfile payload = parsePayload(options.getOrDefault("payload", "text:" + DEFAULT_PAYLOAD_SIZE));

        StreamingProfile streaming = parseStreaming(options, payload);

        FailurePlan failure = FailureParsers.fromSpecification(options.get("failure"));
        long seed = parseLong(options.getOrDefault("seed", "0"), "seed");
        LogFormat logFormat = parseLogFormat(options.getOrDefault("log-format", "json"));
        NoiseProfile noiseProfile = new NoiseProfile(parseNoiseLevel(options.getOrDefault("stderr-rate", "quiet")));
        boolean echoEnv = options.containsKey("echo-env");
        long maxChunks = parseLong(
                options.getOrDefault("stream-max-chunks", String.valueOf(DEFAULT_STREAM_MAX_CHUNKS)),
                "stream-max-chunks");

        return new FixtureConfig(
                mode,
                startupMs,
                bounds,
                payload,
                streaming,
                failure,
                noiseProfile,
                seed,
                logFormat,
                echoEnv,
                maxChunks);
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unrecognised argument: " + arg);
            }
            String raw = arg.substring(2);
            String key;
            String value;
            int equals = raw.indexOf('=');
            if (equals >= 0) {
                key = raw.substring(0, equals);
                value = raw.substring(equals + 1);
            } else {
                key = raw;
                if ("echo-env".equals(key) || "help".equals(key)) {
                    values.put(key, "true");
                    continue;
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --" + key);
                }
                value = args[++i];
            }
            values.put(key, value);
        }
        return values;
    }

    private static FixtureMode parseMode(String value) {
        return FixtureMode.fromFlag(value);
    }

    private static long parseLong(String value, String option) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid value for --" + option + ": " + value, ex);
        }
    }

    private static PayloadProfile parsePayload(String value) {
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Payload must use format <type>:<size>");
        }
        String type = parts[0].toLowerCase(Locale.ROOT);
        int size = Integer.parseInt(parts[1]);
        PayloadFormat format =
                switch (type) {
                    case "text" -> PayloadFormat.TEXT;
                    case "bytes", "base64" -> PayloadFormat.BASE64;
                    default -> throw new IllegalArgumentException("Unknown payload type: " + type);
                };
        return new PayloadProfile(format, size);
    }

    private static StreamingProfile parseStreaming(Map<String, String> options, PayloadProfile payload) {
        String styleRaw = options.getOrDefault("streaming", "smooth");
        StreamingStyle style = StreamingStyle.valueOf(styleRaw.toUpperCase(Locale.ROOT));
        long interval = parseLong(
                options.getOrDefault(
                        "stream-burst-interval-ms", String.valueOf(DEFAULT_STREAMING.burstIntervalMillis())),
                "stream-burst-interval-ms");
        int burstSize = (int) parseLong(
                options.getOrDefault("stream-burst-size", String.valueOf(DEFAULT_STREAMING.burstSize())),
                "stream-burst-size");
        if (style == StreamingStyle.CHUNKED && payload.size() == 0) {
            throw new IllegalArgumentException("chunked streaming requires payload size > 0");
        }
        return new StreamingProfile(style, interval, burstSize);
    }

    private static LogFormat parseLogFormat(String value) {
        return LogFormat.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static NoiseLevel parseNoiseLevel(String value) {
        return NoiseLevel.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
