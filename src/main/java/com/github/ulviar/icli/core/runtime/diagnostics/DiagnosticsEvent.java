package com.github.ulviar.icli.core.runtime.diagnostics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Marker interface for structured diagnostics emitted while draining process output.
 */
public sealed interface DiagnosticsEvent permits DiagnosticsEvent.OutputChunk, DiagnosticsEvent.OutputTruncated {

    /**
     * @return the stream that produced the event.
     */
    StreamType stream();

    /**
     * Event emitted whenever a chunk of output is observed for a streaming capture policy.
     *
     * @param stream  the stream that produced the data
     * @param payload copy of the emitted bytes
     * @param charset charset used to decode the payload for human-readable diagnostics
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Payload arrays are freshly copied by sinks before event construction; accessors return defensive copies.")
    record OutputChunk(StreamType stream, byte[] payload, Charset charset) implements DiagnosticsEvent {

        @Override
        public byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }

        /**
         * Decodes the payload using the provided charset.
         *
         * @param targetCharset charset used to decode the payload
         *
         * @return decoded text representation
         */
        public String text(Charset targetCharset) {
            return new String(payload, targetCharset);
        }
    }

    /**
     * Event emitted when output exceeds the configured retention limit and data is truncated.
     *
     * @param stream         the stream that overflowed
     * @param truncatedChunk preview of the discarded bytes (may be empty when no bytes were retained from the chunk)
     * @param charset        charset used to decode the chunk preview
     * @param retainedBytes  number of bytes kept prior to truncation
     * @param discardedBytes number of bytes discarded for this event
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Preview arrays are freshly copied by the bounded sink prior to event creation; accessor returns defensive copies.")
    record OutputTruncated(
            StreamType stream, byte[] truncatedChunk, Charset charset, long retainedBytes, long discardedBytes)
            implements DiagnosticsEvent {

        public OutputTruncated {
            if (retainedBytes < 0) {
                throw new IllegalArgumentException("retainedBytes must be non-negative");
            }
            if (discardedBytes <= 0) {
                throw new IllegalArgumentException("discardedBytes must be positive");
            }
        }

        @Override
        public byte[] truncatedChunk() {
            return Arrays.copyOf(truncatedChunk, truncatedChunk.length);
        }

        /**
         * Decodes the truncated chunk preview using the provided charset.
         *
         * @param targetCharset charset used to decode the preview
         *
         * @return decoded truncated preview text
         */
        public String preview(Charset targetCharset) {
            return new String(truncatedChunk, targetCharset);
        }
    }
}
