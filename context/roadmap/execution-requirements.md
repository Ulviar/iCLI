# Execution Requirements Brief

## Purpose
- Capture functional and non-functional requirements for iCLI process execution ahead of Phase 3 architecture work.
- Align single-run, interactive, and pooled execution modes with maintainer priorities and legacy lessons learned.
- Provide cross-platform guardrails so Linux, macOS, and Windows (ConPTY) remain first-class targets.

## Scenario summary
- **Single-run commands:** Fire-and-forget executions that collect bounded output and terminate after one invocation.
- **Interactive sessions:** Long-lived processes with streaming IO, optional PTY backing, and lifecycle controls.
- **Pooled workers:** Reusable interactive processes amortising cold starts while preserving isolation guarantees.

## Single-run command requirements
### Core use cases
- Execute common CLI tooling (e.g., `git`, `java`, packaging utilities) with per-call arguments and environment tweaks.
- Support automated scripts that need deterministic exit codes and faithful stdout/stderr capture for logging.
- Run short-lived diagnostics (e.g., `which`, `uname`, `java -version`) during setup flows and health checks.

### IO and control expectations
- Expose both merged and separated stdout/stderr capture with configurable size caps to avoid unbounded memory growth.
- Offer text and raw byte access with explicit charset selection; never assume platform defaults.
- Allow clients to stream output incrementally while the process runs, not only after completion.
- Provide structured results that include command, arguments, exit code, timings, and truncated output indicators.

### Timeout and cancellation policy
- Support caller-specified execution timeouts with a soft kill (`destroy`) followed by configurable hard kill
  escalation.
- Attempt graceful interrupts first (Ctrl+C/SIGINT where available), with optional process-tree termination to prevent
  straggling children before resorting to forceful kill.
- Deliver timeout diagnostics that surface elapsed time, exit state, and whether the process was force-terminated.
- Permit optional deadline-free runs but expose hooks for external cancellation (e.g., `Future` cancellation).

### Cross-platform considerations
- Prefer direct binary execution with explicit argument arrays; fall back to shell wrapping only when required.
- Ensure environment mutation respects platform quoting and path semantics (Windows drive letters, `%VAR%` expansion).
- Stabilise line ending handling by normalising captured text to `\n` while retaining raw bytes when requested.
- Propagate working directory overrides and honour Unicode paths on all supported operating systems.

## Interactive session requirements
### Core use cases
- Drive REPLs and shells (`python`, `node`, `/bin/bash`, `pwsh`) for developer automation and scripting in headless
  environments.
- Automate tools that prompt for credentials or multi-step user input, including expect-style scripting, without
  relying on visible terminal rendering.

- Provide a session handle exposing `stdin`, `stdout`, and `stderr` streams plus high-level helpers (`sendLine`,
  `closeStdin`).
- Allow switching between pipe-based and PTY-backed modes when a TTY is required for prompt-driven flows; window resize
  APIs are not in scope.
- Surface convenience methods for control signals (Ctrl+C, Ctrl+D) without requiring consumers to craft raw bytes.
- Offer optional transcript logging hooks so interactive dialogues can be replayed for debugging without manual capture.
- Offer event hooks or futures for process exit, along with graceful shutdown mechanisms that close stdin or destroy the
  session.

### Session management and timeouts
- Enforce idle and total lifetime limits configurable per session to prevent resource leaks and wedged processes.
- Capture keep-alive metrics (bytes transferred, last command timestamp) to inform pooling or monitoring decisions.
- Detect hung reads or stalled writes, surfacing them via timeouts or callbacks so callers can recover gracefully.

### Cross-platform considerations
- On Unix-like systems, rely on native PTY support when required but avoid assumptions about terminal dimensions or
  colour support.
- On Windows, integrate with ConPTY for TTY detection and prompt handling while keeping visual rendering out of scope.
- Map platform-specific signals (e.g., Ctrl+Break on Windows) to a consistent API surface.
- Keep PTY-backed behaviour consistent across Linux, macOS, and Windows (ConPTY/WinPTY) with documented fallbacks when
  only pipes are available.
- Propagate locale and code page settings to avoid mojibake when interacting with non-UTF-8 environments.

### Out-of-scope behaviours
- Full-screen TUIs (e.g., `top`, curses dashboards) that depend on terminal window sizing or cursor addressing.
- Dynamic PTY resize APIs or TERM negotiation aimed at visual layout fidelity.

## Pooled interactive worker requirements
### Core use cases
- Accelerate invocations of expensive REPL initialisers (e.g., scripting runtimes, compilers) across multiple client
  requests.
- Support lightweight command multiplexing where each request expects a clean process state.
- Enable long-running automations that alternate between computation and CLI interaction without constant restarts.

### Pool semantics
- Provide configuration for pool size limits, max in-flight sessions, and idle eviction intervals.
- Track per-worker usage counts with thresholds that trigger automatic recycling to avoid memory leaks or state drift.
- Propagate process failures to callers, immediately removing unhealthy workers from circulation.
- Offer hooks to warm workers (preload modules, authenticate) and run cleanup routines after each request.

### Isolation and safety
- Reset environment-sensitive state between borrows (working directory, env vars, locale) to guarantee deterministic
  behaviour.
- Ensure stdin/stdout buffers are drained or flushed before returning workers to the pool.
- Support request-level timeouts distinct from worker lifetime limits, forcing restarts when a request exceeds its SLA.
- Capture transcripts or diagnostics per request for observability without leaking data across clients.

### Cross-platform considerations
- Normalise path handling and shell quoting for commands injected into pooled sessions across OS boundaries.
- Allow per-platform tuning (e.g., Windows ConPTY buffer sizing) surfaced through pool configuration.
- Respect OS-level process limits and clean up orphaned child processes when forcefully disposing pooled workers.

## Shared functional requirements
- Provide a unified command specification object that captures binary path, arguments, environment, working directory,
  PTY preference, and shell usage.
- Offer launch options controlling output capture (bounded buffers, discard policies), timeouts, and logging verbosity.
- Expose diagnostics hooks (structured events or callbacks) for process state changes, output truncation, and
  termination, including optional unified transcript logging for troubleshooting.
- Support dependency injection of clock/scheduler components to enable deterministic testing of timeouts and retries.
- Ensure APIs are thread-safe and use virtual threads for IO pumping where advantageous, falling back gracefully if
  unavailable.

## Shared non-functional requirements
- Maintain compatibility with Java 25 features (virtual threads, structured concurrency) without requiring preview
  flags.
- Provide Kotlin-friendly wrappers or usage samples since automated tests and consumers may be authored in Kotlin.
- Integrate with tooling expectations: Spotless formatting, SpotBugs analysis, Gradle task orchestration, and JUnit 6
  tests.
- Document behavioural contracts (timeout ordering, signal semantics, PTY defaults) in public API references and
  knowledge base entries.
- Supply deterministic fixtures and mocks for testing, covering both PTY and non-PTY paths per the testing strategy.

## Risks and mitigations
- **Hanging processes:** Always drain stdout/stderr concurrently and surface APIs for callers to plug custom sinks.
- **Resource leaks:** Enforce structured shutdown (soft then hard kill) and integrate pooling metrics for proactive
  recycling.
- **Cross-platform drift:** Add CI coverage across Linux, macOS, and Windows; flag platform-specific requirements in
  documentation.
- **Encoding issues:** Require explicit charset selection and preserve raw byte access to avoid data loss.

## References
- [Java Terminal & Process Integration — Knowledge Base][kb-pty]
- [Kotlin legacy solution audit][legacy-audit]
- [Testing strategy][testing-strategy]
- [Execution engine benchmarks][exec-benchmarks]

[kb-pty]: ../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md
[legacy-audit]: ../research/experiments/kotlin-solution-audit.md
[testing-strategy]: ../testing/strategy.md
[exec-benchmarks]: ../research/icli-execution-engine-benchmarks.md
