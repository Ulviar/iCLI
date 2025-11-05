package com.github.ulviar.icli.fixture;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

final class FixtureRandom {
    private final RandomGenerator generator;

    FixtureRandom(long seed) {
        this.generator = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    long between(long minInclusive, long maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("max must be >= min");
        }
        if (maxInclusive == minInclusive) {
            return minInclusive;
        }
        long range = maxInclusive - minInclusive + 1;
        long offset = Math.abs(generator.nextLong()) % range;
        return minInclusive + offset;
    }

    double nextDouble() {
        return generator.nextDouble();
    }

    void fill(byte[] target) {
        generator.nextBytes(target);
    }

    int nextInt(int bound) {
        return generator.nextInt(bound);
    }
}
