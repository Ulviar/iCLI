package com.github.ulviar.icli.fixture;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class StreamingController {
    private final FixtureConfig config;
    private final FixtureLogger logger;
    private final FixtureRandom random;
    private final FixtureIO io;
    private final NoiseEmitter noiseEmitter;
    private final LineSessionState sessionState;

    StreamingController(
            FixtureConfig config, FixtureLogger logger, FixtureRandom random, FixtureIO io, NoiseEmitter noiseEmitter) {
        this.config = config;
        this.logger = logger;
        this.random = random;
        this.io = io;
        this.noiseEmitter = noiseEmitter;
        this.sessionState = new LineSessionState(config);
    }

    int run() throws IOException {
        Sleeper.sleepMillis(config.startupDelayMillis());
        logger.startup(config);
        announceReady();
        try (CommandPump pump = new CommandPump(io.reader())) {
            pump.start();
            return emitChunks(pump);
        } finally {
            io.closeQuietly();
        }
    }

    private void announceReady() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", "stream");
        snapshot.put("runtime", SessionSnapshots.runtime(config.runtimeBounds()));
        snapshot.put("payload", SessionSnapshots.payload(config.payloadProfile()));
        snapshot.put("streaming", config.streamingProfile().style().name().toLowerCase());
        logger.ready("ready-stream", snapshot);
        io.stdout().printf("READY-STREAM %s%n", snapshot);
    }

    private int emitChunks(CommandPump pump) {
        StreamLoopState loop = new StreamLoopState();
        while (loop.shouldContinue(config.streamMaxChunks())) {
            int exit = drainCommands(pump, loop);
            if (exit >= 0) {
                return exit;
            }
            if (loop.stopRequested()) {
                break;
            }
            if (loop.isPaused()) {
                Sleeper.sleepMillis(25);
                continue;
            }
            exit = executeRequest(loop);
            if (exit >= 0) {
                return exit;
            }
        }
        logger.streamComplete(loop.emitted(), loop.stopReason());
        io.stdout().printf("STREAM-COMPLETE %s%n", loop.stopReason());
        return 0;
    }

    private int drainCommands(CommandPump pump, StreamLoopState loop) {
        String command;
        while ((command = pump.poll(5)) != null) {
            int exit = applyCommand(command, loop);
            if (exit >= 0) {
                return exit;
            }
        }
        return -1;
    }

    private int applyCommand(String command, StreamLoopState loop) {
        String upper = command.toUpperCase(Locale.ROOT);
        if (upper.startsWith("FAIL")) {
            return CommandParsing.failExitCode(command);
        }
        return switch (upper) {
            case "STOP" -> {
                loop.requestStop("command");
                yield -1;
            }
            case "PAUSE", "HANG" -> {
                loop.pause();
                yield -1;
            }
            case "RESUME" -> {
                loop.resume();
                yield -1;
            }
            case "EXIT" -> 0;
            case "PING" -> {
                io.stdout().printf("STREAM-PONG %d%n", System.currentTimeMillis());
                yield -1;
            }
            default -> handleStructuredCommand(command, loop);
        };
    }

    private int handleStructuredCommand(String command, StreamLoopState loop) {
        if (command.startsWith("{")) {
            loop.override(ControlParser.parseRequest(command));
            return -1;
        }
        if (command.startsWith("CONFIG")) {
            int idx = command.indexOf(' ');
            if (idx >= 0) {
                sessionState.apply(ControlParser.parseConfig(command.substring(idx + 1)));
            }
            return -1;
        }
        return -1;
    }

    private int executeRequest(StreamLoopState loop) {
        RequestOverrides overrides = loop.drainOverride();
        RequestPlan plan = sessionState.plan(overrides);
        FailureEvaluator evaluator = new FailureEvaluator(plan.failurePlan(), random);
        long runtime = random.between(
                plan.runtimeBounds().minMillis(), plan.runtimeBounds().maxMillis());
        logger.requestStart(plan.requestId(), runtime, plan.payloadProfile(), plan.label());
        Sleeper.sleepMillis(runtime);
        noiseEmitter.maybeEmit("stream", plan.requestId());
        FailureEvaluator.Decision decision = evaluator.evaluate(plan.requestId());
        if (decision.type() == FailureEvaluator.DecisionType.HANG) {
            loop.pause();
            return -1;
        }
        FixturePayload payload =
                PayloadGenerator.generate(plan.payloadProfile(), random, plan.requestId(), plan.label());
        io.stdout().printf("CHUNK %d %s%n", plan.requestId(), payload.text());
        StreamingStyle style = plan.streamingStyle();
        logger.chunk(plan.requestId(), payload.size(), style.name().toLowerCase());
        loop.incrementEmitted();
        logger.requestComplete(
                plan.requestId(),
                decision.type() == FailureEvaluator.DecisionType.EXIT ? "failure" : "success",
                decision.exitCode(),
                runtime);
        if (decision.type() == FailureEvaluator.DecisionType.EXIT) {
            return decision.exitCode();
        }
        Sleeper.sleepMillis(
                StreamingDelays.chunkDelay(style, config.streamingProfile(), config.runtimeBounds(), loop.emitted()));
        return -1;
    }

    private static final class StreamLoopState {
        private boolean stopRequested;
        private boolean paused;
        private String stopReason = "max-chunks";
        private long emitted;
        private @Nullable RequestOverrides pendingOverride;

        boolean shouldContinue(long maxChunks) {
            return !stopRequested && emitted < maxChunks;
        }

        boolean stopRequested() {
            return stopRequested;
        }

        String stopReason() {
            return stopReason;
        }

        long emitted() {
            return emitted;
        }

        void incrementEmitted() {
            emitted++;
        }

        boolean isPaused() {
            return paused;
        }

        void pause() {
            paused = true;
        }

        void resume() {
            paused = false;
        }

        void requestStop(String reason) {
            stopRequested = true;
            stopReason = reason;
        }

        void override(RequestOverrides overrides) {
            pendingOverride = overrides;
        }

        RequestOverrides drainOverride() {
            RequestOverrides overrides = pendingOverride;
            pendingOverride = null;
            return overrides;
        }
    }
}
