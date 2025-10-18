# Execution Architecture Brief

## Scope and intent
- Capture the Phase 3 baseline architecture for iCLI’s command execution engine before implementation begins.
- Align single-run, interactive session, and pooled worker capabilities with the execution requirements brief and use
  case catalogue.
- Surface cross-cutting policies (timeouts, PTY handling, diagnostics) so later phases can extend behaviour without
  re-litigating foundation decisions.

## Architecture overview
- Design targets Java 25 with Kotlin-friendly APIs and virtual-thread aware internals.
- Public API exposes immutable configuration types plus orchestrators that return structured results or interactive
  handles.
- Core runtime modularises process launch, IO bridging, lifecycle supervision, and diagnostics to keep each concern
  testable.
- Interactive sessions and worker pools build on the same runtime primitives, ensuring pooling does not re-implement
  process management.
- Reliability remains the primary driver, followed by simplicity (through a streamlined API tier), with flexibility and
  performance considered once the first two priorities are satisfied.

### API composition
- Provide **Essential API** facades (`Commands.run`, `Sessions.start`, `Services.pool`) with safe defaults, no explicit
  timeout or logging configuration required, and simplified result/exception types for consumers who just need command
  outcomes.
- Layer the existing **Advanced API** (`Exec.run`, `LaunchOptions`, `WorkerPool`) beneath the facades for callers who
  must customise timeouts, signals, diagnostics, or PTY behaviour.
- Essential API calls delegate to the advanced layer using opinionated defaults (timeouts, logging off, PTY heuristics)
  while still benefiting from the shared runtime and reliability guarantees.
- Kotlin extension functions mirror both tiers so tests and clients can adopt either entry point idiomatically.

### Essential API behaviour
- **Commands.run** → `CommandResult`. Defaults to merged stdout/stderr text capture with a bounded buffer (default
  64 KiB, configurable via builder or properties) and a conservative timeout (default 60s soft interrupt followed by
  5s grace before force kill). On failure it throws `CommandFailedException` carrying the command echo, exit code,
  truncated output snippets, and a cause when available; callers who need richer diagnostics can drop to the advanced
  API.
- **Sessions.start** → `SimpleSession`. Wraps an interactive handle with helper methods `sendLine`, `readLine`,
  `close`, `closeInputs`, and `onExit`. Default idle timeout (5 minutes) tears down unresponsive sessions, with
  automatic restart available through pooling if desired. Provides lazy access to raw streams for advanced use without
  obligating a full advanced API adoption.
- **Services.pool** → `ServiceProcessor`. Exposes synchronous `process(String input)`, `processBytes(byte[])`, and
  optional async variants (`processAsync`). Internally maintains an automatically sized worker pool (default:
  `min(max(Runtime.availableProcessors() / 2, 1), configuredMax)`) with per-worker reuse cap (default 1 000 requests).
  Errors surface as `ServiceUnavailableException` (pool exhausted or shattered) or `ServiceProcessingException`
  (command exit failure with captured diagnostics). Retries once on recoverable launch errors before bubbling failure.

### Essential API signatures and configuration points
- `CommandResult command = Commands.run(String... command)` — default configuration supplied by
  `EssentialDefaults.command()`; override capture limit, timeout, merge policy, and working directory via overloaded
  methods or Kotlin DSL.
- `SimpleSession session = Sessions.start(String... command)` — accepts optional lambda to receive the underlying
  `SessionHandle` for stream access; idle timeout and PTY preference sourced from `EssentialDefaults.session()`.
- `ServiceProcessor processor = Services.pool(ServiceConfig config)` — `ServiceConfig` captures command, desired
  concurrency, and optional codec strategies. Provides builders to tweak max concurrency, per-request timeout, and
  retry policy while keeping defaults safe. Processors expose lifecycle hooks (`start()`, `close()`) but also support
  auto-start on first request.
- Default values live in `EssentialDefaults` backed by application configuration (e.g., system properties or injected
  config service) with sane fallbacks when nothing is supplied. All defaults prioritise reliability (bounded capture,
  conservative timeouts, graceful shutdown) before throughput.

### Pooling usage modes
- **Simple service pool (Essential API).** Presents a lightweight component (e.g., `Services.pool("mystem")`) that
  exposes single-request helpers such as `process(String input)` or `processBytes(byte[] input)`. Internally it scales
  across multiple warm workers and surfaces either a result or an exception, hiding leases, state resets, and recovery
  logic. Designed for integrations like Lucene token filters that operate “one in, one out” without batching.
- **Batch processor (Essential API, optional).** Builds on the same pool runtime to fan out collections or reactive
  streams of requests. Useful for high-throughput pipelines; can be layered later without changing the core runtime.
- **Lease-driven pool (Advanced API).** Retains the existing `WorkerPool`/`WorkerLease` surface so advanced consumers
  can borrow a process, run custom dialogues, and return it with full control over timeouts, diagnostics, and PTY
  options.

```text
┌───────────────────────────────────────────────────────────────────────────┐
│ API Layer (CommandSpec, LaunchOptions, Exec, Session, Pool facade)        │
├───────────────────────────────────────────────────────────────────────────┤
│ Runtime Core                                                              │
│  • ProcessLauncher (pipes + PTY)                                          │
│  • IOBridge (stdout/stderr pumps, sinks, bounded capture)                 │
│  • TimeoutSupervisor + SignalDispatcher                                   │
│  • DiagnosticsBus (events, transcript taps, logging hooks)                │
├───────────────────────────────────────────────────────────────────────────┤
│ Session Layer                                                             │
│  • InteractiveSessionManager (lifecycle, idle watchdog, handle factory)   │
│  • SessionHandle (stdin/stdout/stderr streams, helpers, exit future)      │
├───────────────────────────────────────────────────────────────────────────┤
│ Pool Layer                                                                │
│  • WorkerPool (configurable leases, warmers, recyclers)                   │
│  • WorkerLease (per-request context, transcript scoping)                  │
└───────────────────────────────────────────────────────────────────────────┘
```

## Module responsibilities

### Command specification module
- Types: `CommandSpec`, `Argument`, `EnvironmentDelta`, `PtyPreference`, `ShellSpec`.
- Responsibilities: capture executable path, argv, working directory, PTY request, shell wrapping, and environment
  overrides; validate invariants (e.g., mutually exclusive shell vs direct execution).
- Integrations: consumed by `ProcessLauncher`; serialised into diagnostics; mirrored by Kotlin DSL builders.

### Launch options module
- Types: `LaunchOptions`, `OutputCapturePolicy`, `TimeoutPolicy`, `SignalPolicy`, `LoggingPolicy`.
- Responsibilities: describe stdout/stderr capture (stream, bounded buffer, discard), charset handling, per-run clocks,
  and cancellation hooks; configure soft versus hard kill sequencing.
- Integrations: injected into runtime core; defaults sourced from configuration service with overrides per invocation.

### Process launcher module
- Types: `ProcessLauncher`, `PipeProcessFactory`, `PtyProcessFactory`, `PlatformDetector`, `ProcessTreeKiller`.
- Responsibilities: start processes using plain pipes or PTY/ConPTY depending on `PtyPreference` and platform; inject
  environment deltas; expose low-level handles to IOBridge; apply shell wrapping only when encoded in `CommandSpec`.
- Integrations: depends on third-party PTY provider (see Dependencies section); consumed by session manager and
  single-run executor.

### IO bridge module
- Types: `IOBridge`, `StreamPump`, `BoundedAccumulator`, `StreamSink`, `TranscriptTap`.
- Responsibilities: drain stdout/stderr concurrently with virtual threads; expose raw byte and decoded text streams;
  enforce capture limits; surface incremental output to observers while retaining structured summaries.
- Integrations: used by single-run executor to return `CompletedProcess`; bound to session handle streams; emits
  events on the diagnostics bus.

### Timeout and signal module
- Types: `TimeoutSupervisor`, `Deadline`, `SignalDispatcher`, `TerminationPlan`.
- Responsibilities: coordinate soft interrupts (Ctrl+C/SIGINT or CTRL_BREAK on Windows) followed by hard termination;
  enforce total and idle timeouts; propagate cancellation back to API callers.
- Integrations: invoked by single-run executor and session manager; publishes timeout diagnostics; relies on
  `ProcessTreeKiller` for recursive termination when requested.

### Diagnostics module
- Types: `DiagnosticsBus`, `ExecutionEvent`, `TranscriptListener`, `MetricsAdapter`.
- Responsibilities: collect structured events (launch, output truncation, timeout, exit); allow pluggable listeners
  (logging, metrics, tracing); provide per-request transcript logging hooks with redaction support.
- Integrations: IO bridge, timeout supervisor, and session manager all emit events; pooling adds lease lifecycle
  events; Kotlin tests can inject probe listeners.

### Interactive session module
- Types: `InteractiveSessionManager`, `SessionHandle`, `SessionLifecycle`, `IdlePolicy`.
- Responsibilities: start and supervise long-lived sessions; expose handle with blocking and async APIs; manage idle
  timers and heartbeat metrics; allow switching between pipe and PTY modes transparently.
- Integrations: built on process launcher and IO bridge; timeout module enforces idle limits; diagnostics capture
  session lifecycle; feeds worker pool.

### Worker pool module
- Types: `WorkerPool`, `PoolConfig`, `WorkerLease`, `WarmupAction`, `RecyclePolicy`.
- Responsibilities: maintain reusable interactive workers; coordinate lease acquisition, per-request execution, reset,
  and eviction; enforce max usage counts and lifetime caps; integrate request-level timeouts distinct from worker
  lifespan.
- Integrations: session manager provides worker instances; diagnostics capture pool health; future metrics integration
  exposes utilisation; Kotlin tests simulate churn and failure recovery.

### Configuration and testing module
- Types: `ExecutionConfig`, `ClockProvider`, `SchedulerFacade`, `TestFixtures`.
- Responsibilities: centralise defaults for timeouts, capture sizes, PTY enablement; support dependency injection for
  deterministic testing; house cross-platform test fixtures referenced in `context/testing/strategy.md`.
- Integrations: consumed by launch options builders and pooling; tests swap in fake clocks or schedulers.

## Data contracts
- `CompletedProcess`: command echo, exit code, timings, stdout/stderr capture (bounded indicators), termination signal,
  diagnostics snapshot.
- `SessionHandle`: exposes `InputStream`, `OutputStream`, `Flow`-style async adapters (optional), `closeStdin`,
  `sendSignal`, `resizePty` (no-op when pipes), `onExit`.
- `CommandResult`: Essential API summary containing merged text output, truncated indicators, elapsed time, and exit
  status.
- `SimpleSession`: Essential API wrapper over `SessionHandle` providing convenience methods, idle enforcement, and
  optional raw stream access.
- `WorkerLease`: wraps session handle with per-request transcript context, reset hooks, and guarantees around state
  isolation.
- `ServiceProcessor`: Essential API projection that routes individual requests to pooled workers while applying builtin
  retries and simplified error reporting.
- `CommandFailedException`, `ServiceUnavailableException`, `ServiceProcessingException`: streamlined exception types
  exposing human-friendly messages plus structured accessors for diagnostics.

## Execution flows

### Single-run command
1. Client builds `CommandSpec` and `LaunchOptions`.
2. `Exec.run` delegates to `ProcessLauncher` to start process.
3. IO bridge pumps streams; diagnostics bus records events.
4. Timeout supervisor monitors deadlines; triggers termination plan on expiry.
5. On exit, runtime assembles `CompletedProcess` with bounded output indicators and event history.

### Interactive session
1. Client calls `Exec.startSession` with `CommandSpec` + `LaunchOptions`.
2. Session manager launches process and returns `SessionHandle`.
3. IO bridge streams into handle-provided `InputStream`/`OutputStream`; diagnostics attach transcript listener if
   configured.
4. Idle policy fires timeouts through timeout supervisor; session manager closes stdin or destroys process on demand.

### Pooled worker request
1. Client acquires `WorkerLease` from pool.
2. Lease wraps session handle, applies request-specific environment deltas, and attaches transcript scope.
3. Request executes (possibly through helper like `lease.runScript(...)`); timeout supervisor enforces request SLA.
4. On completion, pool runs reset hooks (flush buffers, restore cwd/env); recycle policy decides to keep or dispose.

## Cross-cutting concerns
- **Virtual threads:** default for stream pumps and supervision tasks; fall back to platform threads if unavailable.
- **Charset handling:** default to UTF-8 for decoded outputs while always exposing raw bytes; allow per-command
  override.
- **Resource safety:** enforce structured shutdown (soft interrupt → process tree kill) and ensure PTY sessions close
  descriptors promptly.
- **Platform parity:** centralise platform detection, PTY invocation, and signal mapping so behaviour stays consistent
  across Linux, macOS, and Windows (ConPTY).
- **Observability:** diagnostics bus offers hooks for logging, metrics, and optional transcript persistence with
  redaction to avoid leaking secrets.
- **Thread safety:** API types immutable; runtime components designed for concurrent use; pooling synchronises lease
  lifecycle with structured concurrency primitives.
- **Scope guardrails:** full-screen TUIs and terminal window management remain explicitly out of scope; PTY support only
  targets prompt-driven CLI scenarios.

## Dependency considerations
- **PTY provider:** prefer `org.jetbrains.pty4j:pty4j` for cross-platform PTY/ConPTY support; evaluate footprint and
  licensing. Alternative libraries (JNA ConPTY bindings) remain fallback options requiring additional maintenance.
- **Structured logging:** plan to integrate SLF4J facade with user-configurable sinks; confirm no logging impl is forced
  at runtime.
- **Testing aides:** consider embedding lightweight pseudo terminal fixtures for deterministic PTY tests; no external
  services required.

## Open questions and handling
- Confirm whether NuProcess or similar asynchronous process libraries offer tangible benefits over JDK `Process` for
  pipe-based execution, or if native support suffices. *Action:* capture evaluation criteria and, if needed, file a
  research task once implementation reveals performance pressure points.
- Decide on transcript storage strategy (in-memory vs pluggable sinks) to balance diagnostics detail with memory use.
  *Action:* draft design spikes during documentation phase and park the decision in the backlog until observability
  requirements solidify.
- Determine pool configuration persistence: should defaults read from config files, builder DSL, or both? *Action:*
  prototype configuration loading alongside Essential API design to keep onboarding simple.
- Validate minimum Windows version for ConPTY support and document fallback behaviour when unavailable. *Action:* create
  a verification checklist tied to cross-platform testing milestones; no extra research needed today.

## Follow-up work
- Flesh out API signatures and package layout documentation once the brief is ratified.
- Prototype PTY integration via pty4j to validate lifecycle hooks and resource cleanup.
- Draft implementation tasks for single-run executor, session manager, worker pool, Essential service pool facade, and
  optional batch processor to feed the Phase 3 backlog.
