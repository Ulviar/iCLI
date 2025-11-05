# Process Fixture Module

The `process-fixture` module provides the standalone CLI described in
[process-fixture-spec.md](/context/roadmap/process-fixture-spec.md). It offers deterministic single-run, line, and
streaming behaviours so other modules can drive realistic workloads without reaching for external binaries.

## Capabilities
- **Entry point:** `com.github.ulviar.icli.fixture.ProcessFixture` (usable via `:process-fixture:run` or spawned from
  tests using the `FixtureApplication`).
- **Modes:**
  - `single` — emits one payload then exits (respecting startup delay, runtime bounds, payload sizing, and failure
  strategies).
  - `line` — prints `READY { ... }` and processes newline-delimited commands (`PING`, `CONFIG {}`, `RESET`, `FAIL <n>`,
  `HANG`, raw strings, or JSON overrides that tweak payload/runtime/failure per request). Use `mode":"stream"` in a JSON
  request to trigger inline burst-style streaming responses.
  - `stream` — emits chunked events immediately after startup while simultaneously consuming commands (`STOP`, `PAUSE`,
  `RESUME`, `CONFIG {}`, JSON overrides) via stdin. Streaming completes automatically after `--stream-max-chunks` chunks
  or when a command requests shutdown.
- **Logging:** Structured JSON (default) or text logs capture startup → request/chunk start → completion transitions.
- **Determinism:** Every stochastic behaviour derives from `--seed=<long>` so tests can replay identical sequences.

## Key CLI flags

| Flag                                                                      | Description                                    |
|---------------------------------------------------------------------------|------------------------------------------------|
| `--mode=<single\|line\|stream>`                                           | Selects the execution style. |
| `--startup-ms=<millis>`                                                   | Delay before signalling readiness.             |
| `--runtime-min-ms/--runtime-max-ms`                                       | Random delay bounds per request/chunk.         |
| `--payload=<text\|base64>:<size>`                                         | Controls payload encoding and target size. |
| `--streaming=<smooth\|burst\|chunked>` + `--stream-burst-size/interval-ms` | Tunes streaming cadence. |
| `--failure=<never\|random:p\|at:n\|exit-code:n\|hang[:n]>`                | Injects exit codes or hangs deterministically. |
| `--stderr-rate=<quiet\|normal\|loud>`                                     | Emits noise on stderr; combine with `--echo-env` to dump env vars. |
| `--stream-max-chunks=<n>`                                                 | Caps streaming sessions to keep tests bounded. |
| `--log-format=<json\|text>`                                               | Toggles diagnostics formatting. |

Run `./gradlew :process-fixture:run --args "--help"` for the latest synopsis.

## Control commands

All interactive modes accept the following line-oriented commands over stdin:

- `PING` → emits `PONG <timestamp>`.
- `EXIT` → shuts down gracefully (exit code `0`).
- `FAIL <code>` → forces a specific exit code immediately.
- `CONFIG { ... }` → applies a JSON delta (`runtimeMinMs`, `runtimeMaxMs`, `payload`, `streaming`, `fail`).
- `RESET` (line mode) → restores the original configuration and clears hang state.
- `STOP` / `PAUSE` / `RESUME` (stream mode) → control chunk emission.
- Raw JSON objects (without a `CONFIG` prefix) → per-request overrides (`payload`, `fail`, `mode`, `chunks`, `label`).

## Building & testing

Invoke Gradle via the repository helper to build or run the test suite (Java 25 toolchain required):

```bash
python scripts/run_mcp.py execute_gradle_task --project :process-fixture --tasks build
python scripts/run_mcp.py execute_gradle_task --project :process-fixture --tasks test
```

Other modules (root + `samples`) already declare `testImplementation(project(":process-fixture"))`, so tests can reuse
fixture helpers directly or launch the CLI via `ProcessFixture`.
