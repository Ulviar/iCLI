package com.github.ulviar.icli.fixture;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class LineSessionController {
    private final FixtureConfig config;
    private final FixtureLogger logger;
    private final FixtureRandom random;
    private final FixtureIO io;
    private final NoiseEmitter noiseEmitter;
    private final LineSessionState state;

    LineSessionController(
            FixtureConfig config, FixtureLogger logger, FixtureRandom random, FixtureIO io, NoiseEmitter noiseEmitter) {
        this.config = config;
        this.logger = logger;
        this.random = random;
        this.io = io;
        this.noiseEmitter = noiseEmitter;
        this.state = new LineSessionState(config);
    }

    int run() throws IOException {
        Sleeper.sleepMillis(config.startupDelayMillis());
        logger.startup(config);
        announceReady();
        BufferedReader reader = io.reader();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (state.isHanging() && !trimmed.equalsIgnoreCase("RESET")) {
                continue;
            }
            int exit = handleCommand(trimmed);
            if (exit >= 0) {
                io.closeQuietly();
                return exit;
            }
        }
        io.closeQuietly();
        return 0;
    }

    private void announceReady() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", "line");
        snapshot.put("runtime", SessionSnapshots.runtime(config.runtimeBounds()));
        snapshot.put("payload", SessionSnapshots.payload(config.payloadProfile()));
        logger.ready("ready-line", snapshot);
        io.stdout().printf("READY %s%n", snapshot);
    }

    private int handleCommand(String command) {
        String upper = command.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PING" -> {
                io.stdout().printf("PONG %d%n", System.currentTimeMillis());
                yield -1;
            }
            case "EXIT" -> 0;
            case "HANG" -> {
                state.enterHang();
                yield -1;
            }
            case "RESET" -> {
                state.reset();
                io.stdout().println("RESET-OK");
                yield -1;
            }
            default -> handlePrefixedCommand(command, upper);
        };
    }

    private int handlePrefixedCommand(String command, String upper) {
        if (upper.startsWith("FAIL")) {
            return CommandParsing.failExitCode(command);
        }
        if (upper.startsWith("CONFIG")) {
            applyConfig(command);
            return -1;
        }
        if (command.startsWith("{")) {
            return processRequest(ControlParser.parseRequest(command), null);
        }
        return processRequest(null, command);
    }

    private void applyConfig(String command) {
        int idx = command.indexOf(' ');
        if (idx < 0) {
            throw new IllegalArgumentException("CONFIG requires JSON payload");
        }
        String json = command.substring(idx + 1).trim();
        state.apply(ControlParser.parseConfig(json));
        io.stdout().println("CONFIG-OK");
    }

    private int processRequest(@Nullable RequestOverrides overrides, @Nullable String inlinePayload) {
        RequestPlan plan = state.plan(overrides);
        FailureEvaluator evaluator = new FailureEvaluator(plan.failurePlan(), random);
        long runtime = random.between(
                plan.runtimeBounds().minMillis(), plan.runtimeBounds().maxMillis());
        logger.requestStart(plan.requestId(), runtime, plan.payloadProfile(), plan.label());
        Sleeper.sleepMillis(runtime);
        noiseEmitter.maybeEmit("line", plan.requestId());

        FailureEvaluator.Decision decision = evaluator.evaluate(plan.requestId());
        if (decision.type() == FailureEvaluator.DecisionType.HANG) {
            state.enterHang();
            return -1;
        }

        if (plan.mode() == FixtureMode.STREAM) {
            runInlineStream(plan);
        } else {
            emitLineResponse(plan, inlinePayload);
        }

        logger.requestComplete(
                plan.requestId(),
                decision.type() == FailureEvaluator.DecisionType.EXIT ? "failure" : "success",
                decision.exitCode(),
                runtime);
        return decision.type() == FailureEvaluator.DecisionType.EXIT ? decision.exitCode() : -1;
    }

    private void emitLineResponse(RequestPlan plan, @Nullable String inlinePayload) {
        String payloadText = inlinePayload != null
                ? inlinePayload
                : PayloadGenerator.generate(plan.payloadProfile(), random, plan.requestId(), plan.label())
                        .text();
        io.stdout().printf("RESULT %d %s%n", plan.requestId(), payloadText);
        logger.lineResponse(plan.requestId(), plan.label(), payloadText.length());
    }

    private void runInlineStream(RequestPlan plan) {
        long limit = plan.chunkCount() != null ? plan.chunkCount() : config.streamMaxChunks();
        StreamingStyle style = plan.streamingStyle();
        for (long chunk = 1; chunk <= limit; chunk++) {
            FixturePayload payload =
                    PayloadGenerator.generate(plan.payloadProfile(), random, plan.requestId(), plan.label());
            io.stdout().printf("CHUNK %d %s%n", chunk, payload.text());
            logger.chunk(chunk, payload.size(), style.name().toLowerCase());
            Sleeper.sleepMillis(
                    StreamingDelays.chunkDelay(style, config.streamingProfile(), config.runtimeBounds(), chunk));
        }
        logger.streamComplete(limit, "inline-request");
        io.stdout().println("STREAM-COMPLETE inline-request");
    }
}
