package com.github.ulviar.icli.fixture;

final class FailureEvaluator {
    enum DecisionType {
        NONE,
        EXIT,
        HANG
    }

    record Decision(DecisionType type, int exitCode) {
        static Decision none() {
            return new Decision(DecisionType.NONE, 0);
        }

        static Decision exit(int code) {
            return new Decision(DecisionType.EXIT, code);
        }

        static Decision hang() {
            return new Decision(DecisionType.HANG, 0);
        }
    }

    private final FailurePlan plan;
    private final FixtureRandom random;

    FailureEvaluator(FailurePlan plan, FixtureRandom random) {
        this.plan = plan;
        this.random = random;
    }

    Decision evaluate(long requestIndex) {
        if (plan instanceof FailurePlan.Never) {
            return Decision.none();
        }
        if (plan instanceof FailurePlan.RandomFailure randomFailure) {
            if (random.nextDouble() < randomFailure.probability()) {
                return Decision.exit(randomFailure.exitCode());
            }
            return Decision.none();
        }
        if (plan instanceof FailurePlan.AtRequest atRequest) {
            if (requestIndex == atRequest.requestIndex()) {
                return Decision.exit(atRequest.exitCode());
            }
            return Decision.none();
        }
        if (plan instanceof FailurePlan.ExitCodeFailure exitCodeFailure) {
            if (requestIndex >= 1) {
                return Decision.exit(exitCodeFailure.exitCode());
            }
            return Decision.none();
        }
        if (plan instanceof FailurePlan.HangFailure hangFailure) {
            if (requestIndex >= hangFailure.requestIndex()) {
                return Decision.hang();
            }
        }
        return Decision.none();
    }
}
