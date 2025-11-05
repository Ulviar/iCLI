package com.github.ulviar.icli.fixture;

final class SingleRunController {
    private final FixtureConfig config;
    private final FixtureLogger logger;
    private final FixtureRandom random;
    private final FixtureIO io;
    private final NoiseEmitter noiseEmitter;

    SingleRunController(
            FixtureConfig config, FixtureLogger logger, FixtureRandom random, FixtureIO io, NoiseEmitter noiseEmitter) {
        this.config = config;
        this.logger = logger;
        this.random = random;
        this.io = io;
        this.noiseEmitter = noiseEmitter;
    }

    int run() {
        Sleeper.sleepMillis(config.startupDelayMillis());
        logger.startup(config);
        FailureEvaluator evaluator = new FailureEvaluator(config.failurePlan(), random);
        long requestId = 1L;
        long runtime = random.between(
                config.runtimeBounds().minMillis(), config.runtimeBounds().maxMillis());
        logger.requestStart(requestId, runtime, config.payloadProfile(), null);
        Sleeper.sleepMillis(runtime);
        noiseEmitter.maybeEmit("single", requestId);
        FailureEvaluator.Decision decision = evaluator.evaluate(requestId);
        if (decision.type() == FailureEvaluator.DecisionType.HANG) {
            hang();
        }
        FixturePayload payload = PayloadGenerator.generate(config.payloadProfile(), random, requestId, null);
        io.stdout().printf("PAYLOAD %d %s%n", requestId, payload.text());
        logger.requestComplete(
                requestId,
                decision.type() == FailureEvaluator.DecisionType.EXIT ? "failure" : "success",
                decision.exitCode(),
                runtime);
        io.closeQuietly();
        return decision.type() == FailureEvaluator.DecisionType.EXIT ? decision.exitCode() : 0;
    }

    private static void hang() {
        while (true) {
            Sleeper.sleepMillis(5_000);
        }
    }
}
