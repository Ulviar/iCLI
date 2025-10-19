package com.github.ulviar.icli.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

/**
 * Strategy describing how command output should be captured.
 *
 * <p>Policies may retain bounded in-memory buffers, stream to consumers, or discard output entirely.
 */
public sealed interface OutputCapturePolicy
        permits OutputCapturePolicy.Bounded, OutputCapturePolicy.Streaming, OutputCapturePolicy.Discard {

    /** Maximum bytes retained in memory, if applicable. */
    OptionalLong maxRetainedBytes();

    /** Charset used when decoding output for summary views. */
    Charset charset();

    /** Whether the policy streams output incrementally. */
    boolean isStreaming();

    /** Bounded in-memory capture retaining up to {@link #maxBytes()} bytes. */
    record Bounded(long maxBytes, Charset charset) implements OutputCapturePolicy {
        public Bounded {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
        }

        @Override
        public OptionalLong maxRetainedBytes() {
            return OptionalLong.of(maxBytes);
        }

        @Override
        public Charset charset() {
            return charset;
        }

        @Override
        public boolean isStreaming() {
            return false;
        }
    }

    /** Streaming capture that passes data to subscribers without retaining it. */
    record Streaming(Charset charset) implements OutputCapturePolicy {
        @Override
        public OptionalLong maxRetainedBytes() {
            return OptionalLong.empty();
        }

        @Override
        public Charset charset() {
            return charset;
        }

        @Override
        public boolean isStreaming() {
            return true;
        }
    }

    /** Discards output entirely. */
    record Discard() implements OutputCapturePolicy {
        @Override
        public OptionalLong maxRetainedBytes() {
            return OptionalLong.empty();
        }

        @Override
        public Charset charset() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public boolean isStreaming() {
            return false;
        }
    }

    static OutputCapturePolicy bounded(long maxBytes) {
        return new Bounded(maxBytes, StandardCharsets.UTF_8);
    }

    static OutputCapturePolicy streaming() {
        return new Streaming(StandardCharsets.UTF_8);
    }

    static OutputCapturePolicy discard() {
        return new Discard();
    }
}
