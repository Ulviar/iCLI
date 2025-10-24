package com.github.ulviar.icli.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Result produced by the advanced execution engine after a command completes.
 *
 * <p>Fields represent the minimum surface needed by the client API. The runtime implementation will enrich this
 * record with additional diagnostics as the project evolves.
 */
public record ProcessResult(int exitCode, String stdout, String stderr, Optional<Duration> duration) {}
