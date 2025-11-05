# Process Fixture Program — Technical Specification

## Purpose
- Provide a deterministic-yet-configurable external process that exercises iCLI across one-shot, interactive, and
  streaming workflows.
- Support integration, stress, and soak tests by simulating variable startup times, per-request runtimes, streaming
  behaviours, output sizes, and failure modes.
- Serve as a reusable module (e.g., `process-fixture`) that can be called directly from tests or spawned manually for
  debugging.

## High-level capabilities
1. **Launch-time configuration** via CLI flags or config file (opt-in) controlling:
   - Startup delay (`--startup-ms`): time between process launch and readiness.
   - Mode selection (`--mode=single|line|stream`), default `single`.
   - Default per-request runtime bounds (`--runtime-min-ms`, `--runtime-max-ms`).
   - Output profile (`--payload=size:bytes|text`, `--streaming=burst|chunked|smooth`).
   - Failure strategy (`--failure=never|random|at=<request>|hang|exit-code=<n>`).
   - Noise injection (stderr chatter, environment echo) for diagnostics tests.
   - Random seed to make stochastic scenarios reproducible.
2. **Dynamic control primitives** accepted over stdin (JSON or simple tokens) to override defaults per request:
   {payload, runtime, mode, fail}. Enables tests to script sequences without restarting the process.
3. **Reporting hooks**: structured stdout (JSON lines) describing each phase (startup, request start, chunk sent,
   completion) plus optional plain-text responses for compatibility with existing line/stream runners.

## Execution modes
### Single-run (`--mode=single`)
- Behaves like a traditional CLI: accepts command-line args describing workload, sleeps for startup, performs work
  (random duration within bounds), and exits with configured status.
- Outputs optional artifacts: text blob, binary payload (hex/base64), stderr diagnostics.
- Supports bounded outputs (exact size) to stress capture policies.

### Line session (`--mode=line`)
- Prints `READY` once startup completes.
- For each newline-terminated input:
  1. Parse request descriptor (plain string or JSON envelope specifying runtime/payload/failure overrides).
  2. Sleep random duration within current bounds.
  3. Emit response line (optionally large) respecting payload profile.
  4. Optionally emit stderr side-channel logs.
- Commands: `PING`, `EXIT`, `CONFIG {...}` (changes runtime/payload), `FAIL <code>`, `HANG` (never respond).
- Reset hook: typing `RESET` returns to defaults.

### Streaming (`--mode=stream`)
- After startup, begins emitting framed events without waiting for input, while still consuming control commands (e.g.,
  to adjust payload or request stop).
- Streaming profiles:
  - `smooth`: evenly spaced chunks (e.g., every 25ms) until runtime elapses.
  - `burst`: send bursts separated by idle gaps to test buffering/backpressure.
  - `chunked`: deterministic chunk sizes (configurable) to test boundary cases.
- Supports `CTRL` commands on stdin to pause/resume, change chunk size, or trigger graceful stop vs abrupt failure.
- Optionally produces structured metadata (timestamps, chunk index) for assertions.

## Failure & jitter scenarios
- **Timeout emulation:** allow requests to deliberately exceed the configured runtime or ignore stdin to test idle
  timeouts.
- **Exit codes:** map specific commands to exit codes (success, user error, fatal crash).
- **Process-tree simulation:** spawn child processes that linger to test destroyProcessTree logic.
- **Resource spike:** optionally emit large bursts to stdout/stderr to stress bounded capture and streaming sinks.

## Observability
- Provide a `--log-format={text,json}` flag.
- Every phase should be timestamped and include request identifiers (incremental counter + UUID) to match diagnostics.
- When running in line/stream modes, include a `READY` banner summarising configuration for easier debugging.

## Module expectations
- New Gradle module `process-fixture` under the repo root, with its own [AGENTS.md](/AGENTS.md), README, and tests.
- Implemented in Java 25 (to match main project) with Kotlin tests allowed.
- Expose a simple CLI entry point (e.g., `com.github.ulviar.icli.fixture.ProcessFixture`) so integration tests can
  invoke it via `TestProcessCommand`-style helpers.
- Provide a lightweight client wrapper for reuse within tests (optional but encouraged).

## Test scenarios enabled
1. **Single-run deterministic vs random durations** — ensures timeout + shutdown sequencing.
2. **Line session concurrency** — multiple pooled workers hitting `line` mode to validate pool reuse.
3. **Streaming listen-only** — verifying smooth vs burst publishers, stopStreaming semantics, and diagnostics.
4. **Failure injection** — commands that hang, exit with codes, or spawn stubborn children.
5. **Payload extremes** — zero-byte responses, multi-megabyte bursts, high-frequency chunking.
6. **Config mutation** — `CONFIG` requests mid-session to confirm stateful conversations behave.
7. **Backpressure** — streaming mode with configurable throttle to evaluate `ListenOnlySessionClient` behaviour under
   slow consumers.

## CLI sketch
```
process-fixture \
  --mode=line \
  --startup-ms=250 \
  --runtime-min-ms=50 \
  --runtime-max-ms=150 \
  --payload=text:64 \
  --streaming=burst \
  --seed=1234 \
  --failure=random:0.1 \
  --stderr-rate=medium
```
- Flags may be combined with per-request overrides (JSON via stdin) for fine control.

## Deliverables
- New module source, README, and integration tests proving each mode works.
- Gradle wiring so other modules/tests can depend on the fixture.
- Documentation linking this spec to future tasks (ICLI-042) and referencing integration scenarios.
