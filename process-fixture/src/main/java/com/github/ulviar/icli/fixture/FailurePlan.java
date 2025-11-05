package com.github.ulviar.icli.fixture;

/**
 * Describes when and how the fixture should fail.
 */
public sealed interface FailurePlan
        permits FailurePlan.Never,
                FailurePlan.RandomFailure,
                FailurePlan.AtRequest,
                FailurePlan.ExitCodeFailure,
                FailurePlan.HangFailure {

    FailurePlan NEVER = new Never();

    record Never() implements FailurePlan {}

    record RandomFailure(double probability, int exitCode) implements FailurePlan {
        public RandomFailure {
            if (probability < 0.0d || probability > 1.0d) {
                throw new IllegalArgumentException("probability must be between 0 and 1");
            }
        }
    }

    record AtRequest(long requestIndex, int exitCode) implements FailurePlan {
        public AtRequest {
            if (requestIndex <= 0) {
                throw new IllegalArgumentException("requestIndex must be positive");
            }
        }
    }

    record ExitCodeFailure(int exitCode) implements FailurePlan {}

    record HangFailure(long requestIndex) implements FailurePlan {
        public HangFailure {
            if (requestIndex < 0) {
                throw new IllegalArgumentException("requestIndex must be >= 0");
            }
        }
    }
}
