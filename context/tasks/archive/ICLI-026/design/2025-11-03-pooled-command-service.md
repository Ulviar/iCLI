# Pooled Command Service Design (ICLI-026)

## Goals
- Capture the target Essential API layout for pooled workflows so follow-up tasks (ICLI-027–ICLI-030) can implement the
  design without re-litigating requirements.
- Base the proposal on the 2025-11-03 analysis direction for ICLI-016 and the roadmap/architecture briefs.
- Preserve backwards compatibility for existing `CommandService.pooled()` callers while charting a migration toward the
  new facade.

## Non-goals
- Implementing the new service, runners, or shared components.
- Redesigning pool internals or diagnostics contracts already defined in the process pool architecture.
- Delivering affinity, listen-only streaming, or MCP integrations (covered by other backlog items).

## Current state
- `CommandService` exposes `CommandRunner`, `LineSessionRunner`, and `InteractiveSessionRunner` by copying the same
  default-building logic (base `CommandCall`, decoder, scheduler) into each runner. `InteractiveSessionStarter` handles
  PTY fallback, but there is no shared abstraction for building calls or launching sessions.
- `CommandService.pooled()` returns a new `ProcessPoolClient` each time. The helper copies service defaults into
  `ProcessPoolConfig.Builder`, lets callers customise the builder, and immediately constructs the pool. Callers must
  close the returned client explicitly; the service itself does not retain or reuse the pool.
- `ProcessPoolClient` wraps the advanced `ProcessPool` and exposes `serviceProcessor()` (stateless request helper),
  `openConversation()` (stateful handle around `WorkerLease`), and `pool()` for advanced access. These APIs do not map
  cleanly onto the runner mental model exposed by `CommandService`.
- `ServiceProcessor` and `ServiceConversation` wire `ServiceProcessorListener` callbacks, perform manual resets, and
  translate exceptions into `CommandResult` failures. Nothing in the current `CommandService` helps coordinate listener
  usage or reuse these policies for non-pooled flows.
- Documentation and samples describe `CommandService` runners, while pooled usage is explained through
  `ProcessPoolClient` terminology. There is no single “pooled service” facade paralleling the standard Essential API.

## Target structure

### Shared execution core (ICLI-027 scope)
- Introduce `RunnerDefaults` (package-private record in `com.github.ulviar.icli.client.internal.runner`) that stores the
  shared `CommandDefinition`, `ExecutionOptions`, and default `ResponseDecoder`. The record supplies `createBaseCall()`
  and `buildCall(Consumer<CommandCallBuilder>)` helpers so every runner builds calls identically.
- Add `CommandCallFactory` (same package) that wraps `RunnerDefaults` and centralises creation of `CommandCall` and
  `CommandCallBuilder`. Existing runners will delegate to it; pooled runners will share the same logic to remain
  aligned.
- Extract a small `SessionLauncher` functional interface used by both `InteractiveSessionStarter` and pooled
  conversation adapters. The launcher accepts a `CommandCall` and returns `InteractiveSessionClient`, abstracting PTY
  fallback versus lease acquisition.
- Provide a `LineSessionFactory` helper that accepts an `InteractiveSessionClient`, `ResponseDecoder`, and
  `ClientScheduler` to produce a `LineSessionClient`. Both direct and pooled runners will call into it.

### PooledCommandService facade
- `PooledCommandService` lives under `com.github.ulviar.icli.client.pooled`, implements `AutoCloseable`, and owns a
  single `ProcessPoolClient` created during construction. Closing the service closes the client and drains the pool.
- The service mirrors `CommandService` constructors: defaults (`ExecutionOptions.builder().build()` and
  `ClientSchedulers.virtualThreads()`), full customisation (options, scheduler, listener), plus overloads that accept a
  `Consumer<ProcessPoolConfig.Builder>` for pool tweaks before the config is built.
- Internally the service composes a `RunnerDefaults` instance so pooled runners share the same command, options, and
  decoder as non-pooled flows. The defaults also feed a `ProcessPoolConfig.Builder` helper that applies pool-specific
  policies (worker options, destroy-process-tree) before customisation.
- Public API surface:
  - `PooledCommandRunner commandRunner()` — stateless helper for request/response workflows.
  - `PooledLineSessionRunner lineSessionRunner()` — acquires leases and exposes line-oriented conversations.
  - `PooledInteractiveSessionRunner interactiveSessionRunner()` — acquires leases for full interactive control.
  - `ProcessPoolClient advancedClient()` — returns the owned client for advanced usage (same reference, not a clone).
- The service exposes optional `PoolMetrics snapshot()` and `ServiceProcessorListener listener()` accessors for
  observability parity with `ProcessPoolClient`.

### Pooled runner responsibilities
- `PooledCommandRunner` wraps a cached `ServiceProcessor`. Methods:
  - `CommandResult<String> process(String input)`
  - `CompletableFuture<CommandResult<String>> processAsync(String input)`
  - `PooledCommandRunner withDecoder(ResponseDecoder)` — returns a new runner sharing the same pool but overriding the
  decoder (useful for alternate framing). Implemented by constructing a new `ServiceProcessor` on demand. The overload
  only accepts `ResponseDecoder` instances; we will provide a curated set of built-ins (newline, timeout-aware,
  sentinel, regex) while keeping the SPI open for callers to supply custom implementations.
- `PooledLineSessionRunner` exposes `PooledLineConversation open()` / `open(Consumer<CommandCallBuilder>)`. A
  conversation retains a `ServiceConversation`, returns a `LineSessionClient` via `line()`, and offers `reset()`,
  `retire()`, `scope()`, plus convenience `process` helpers mirroring `LineSessionClient`.
- `PooledInteractiveSessionRunner` exposes `PooledInteractiveConversation open()` / `open(customiser)`. The conversation
  delegates to `ServiceConversation.interactive()`, exposes raw stream access, and surfaces the same lifecycle helpers.
- Both conversation types implement `AutoCloseable`, forwarding `close()` and `retire()` to the underlying
  `ServiceConversation`. They accept the service-level listener and apply it to all events.
- Pooled runners reuse `RunnerDefaults` for call construction and `LineSessionFactory` for wrapping sessions, ensuring
  parity with standard runners.

### Diagnostics and listener composition
- `PooledCommandService` constructor accepts an optional `ServiceProcessorListener` (default
  `ServiceProcessorListener.noOp()`). The listener is shared across all pooled runners and passed to underlying
  `ServiceProcessor`/`ServiceConversation` instances.
- Provide `PooledCommandService withListener(ServiceProcessorListener)` to derive a new facade sharing the same pool but
  swapping listeners (cheap cloning). This allows scenarios that need alternate telemetry without rebuilding the pool.
- Document that runners do not support per-call listener overrides in the initial implementation; callers requiring
  bespoke diagnostics should create separate service instances or wrap the listener.
- Pool-level diagnostics remain configured via `ProcessPoolConfig.Builder#diagnosticsListener`. The service records both
  listeners in the design note to prevent confusion between pool-level events and request-level callbacks.
- Metrics remain part of the Advanced API: callers retrieve them via `ProcessPoolClient` (or directly from
  `ProcessPool`) while the Essential facade stays focused on the “request → result/error” workflow. Documentation will
  highlight this split to preserve Essential API simplicity.

### Package layout and module exports
- Add `com.github.ulviar.icli.client.pooled` package to host the new service, runners, and conversation types. Keep
  helper types (e.g., `RunnerDefaults`) in `client.internal.runner` to avoid expanding the public surface.
- Update `module-info.java` to `exports com.github.ulviar.icli.client.pooled;` while leaving existing exports intact.
- Move `ServiceProcessor`, `ServiceConversation`, and `ServiceProcessorListener` into the `pooled` package once the new
  facade is in place (ICLI-030 cleanup). During transition they remain in `com.github.ulviar.icli.client` but become
  package-private where possible.

## Migration strategy
- Phase 1 (ICLI-028): introduce `PooledCommandService` and add new factories on `CommandService`:
  - `public PooledCommandService pooledService()` returning a new facade configured with service defaults.
  - Existing `pooled(...)` overloads continue returning `ProcessPoolClient` but delegate to
  `pooledService().advancedClient()` so behaviour stays identical.
- Phase 2 (ICLI-029): update documentation, samples, and roadmap briefs to prefer `PooledCommandService`. Highlight the
  new runners and show how they map to the execution use-case catalogue.
- Phase 3 (ICLI-030): deprecate `CommandService.pooled()` overloads that expose `ProcessPoolClient`, reposition
  `ProcessPoolClient` as an advanced entry point, and document migration guidance. Once consumers adopt the new facade,
  future major versions may remove the legacy overloads.

## Follow-up mapping
- **ICLI-027** — Implement `RunnerDefaults`, `CommandCallFactory`, `SessionLauncher`, and `LineSessionFactory`; refactor
  existing runners to use them and add unit coverage for the shared layer.
- **ICLI-028** — Add `PooledCommandService`, pooled runners/conversations, and integrate them with the shared layer and
  existing pool runtime. Ensure Kotlin tests exercise command processing, line conversations, and interactive sessions.
- **ICLI-029** — Refresh README, architecture brief, and examples to describe both standard and pooled services; update
  task dossiers referencing runner usage.
- **ICLI-030** — Finalise migration: deprecate or relocate `ProcessPoolClient`, tighten module exports, and ensure
  diagnostics/listener wiring matches this design.

## Open questions
- Should `PooledCommandRunner.withDecoder(...)` accept arbitrary codecs or only reuse `ResponseDecoder` instances? Need
  maintainer guidance before implementing extensibility.
- Do we expose `PoolMetrics snapshot()` directly on `PooledCommandService`, or should callers go through
  `advancedClient()` to avoid API duplication?
- Confirm whether conversation wrappers should surface `ClientScheduler` for async helpers beyond the provided
  `processAsync` methods.
- Determine if we can relax access on `ServiceProcessor`/`ServiceConversation` during transition without breaking
  existing consumers; if not, we may need to leave them public until the cleanup task completes.
- Pooled conversations will not expose the underlying `ClientScheduler` directly; the shared helpers already cover the
  required async entry points, and we can revisit if richer hooks are needed later.
- Current API surface is deliberately fluid: no external consumers rely on these classes yet, so we may adjust method
  signatures and packaging freely until the 1.0 release plan hardens. This note will be removed once we lock the public
  contract.
