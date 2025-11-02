# iCLI Command Execution Library — Roadmap

> **Scenario alignment.** Every roadmap milestone and API task must trace back to the authoritative > > > > > > > > > >
> > > > > > > > > > > > > > > > > > [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md).
> Before proposing > new > > > > > features > or > > > > > modifying > > > > > > > > > > > > > > > existing ones,
> confirm the scenario > catalogue > > covers > > > the intended > > > workflow; if > > > not, update > the > > >
> catalogue > > > first.

## Phase 1 — Foundations
- ✅ Audit existing Kotlin solution to identify reusable ideas, pain points, and missing capabilities.
- ✅ Establish repository guidelines, coding standards, and project conventions (formatting, testing, CI expectations).
  *Published in
  [context/guidelines/general/contributor-guidelines.md](/context/guidelines/general/contributor-guidelines.md).*

## Phase 2 — Discovery & Architecture
- ✅ Map functional requirements: single-shot execution, long-running interactive sessions, pooled interactive workers
  (documented in [execution-requirements.md](/context/roadmap/execution-requirements.md)).
- ✅ Catalogue maintainer-sourced use cases (e.g., `mystem`, shells, REPLs) and non-functional constraints
  (cross-platform, resource usage) (documented in
  [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md)).
- ✅ Research JVM ecosystem options for PTY/ConPTY (pty4j, Apache Commons Exec, JNA, Jansi) and decide on dependencies —
  adopt **pty4j** (EPL-1.0) as the baseline PTY backend, record legal follow-up, and track WinPTY→ConPTY migration needs
  alongside [icli-execution-engine-benchmarks.md](/context/research/icli-execution-engine-benchmarks.md).
- ✅ Draft high-level architecture: process abstraction layer, session lifecycle, pooling strategy, error and timeout
  model (see [execution-architecture-brief.md](/context/roadmap/execution-architecture-brief.md)).
- ✅ Evaluate support for both blocking and non-blocking client APIs, documenting the hybrid strategy and surfacing async
  scheduler + futures in the client layer (captured in ICLI-007/ICLI-012 dossiers).
- ✅ Define data contracts (command specification, execution options, result types) and public API surface (captured in
  the "Data contracts" section of [execution-architecture-brief.md](/context/roadmap/execution-architecture-brief.md)).

## Phase 3 — Core Execution Engine
- ✅ Implement command specification builder with explicit handling for shell vs direct execution and environment
  overrides (CommandDefinition + ShellConfiguration shipped).
- ✅ Build single command executor: process launch, bounded output capture, exit status interpretation, timeout and
  cancellation hooks (StandardProcessEngine + ExecutionOptions defaults).
- ✅ Add comprehensive error handling and diagnostics (logs, exception hierarchy, structured result objects) with
  ProcessEngineExecutionException, diagnostics bus, and structured ProcessResult in place.
- ✅ Create initial Kotlin-based integration tests (JUnit 6) covering success, failure, timeouts, large output, encoding
  handling (integration suite under `src/integrationTest/kotlin`).

## Phase 4 — Interactive Session Support
- ✅ Implement interactive session API returning a handle with streaming IO and lifecycle controls suitable for headless
  PTY interactions (InteractiveSession + ProcessInteractiveSession clients).
- ✅ Support both pipe-based and PTY-backed sessions; abstract differences behind a unified interface (TerminalAware
  launcher with pipe/PTY implementations).
- ✅ Provide expect-style helper utilities for scripted interactions and build sample usage documentation (LineSession
  helpers and README quick start).
- ✅ Extend test coverage with simulated REPLs, echo servers, and PTY-required prompt workflows (TestProcess-driven
  integration suite; visual TUIs remain out of scope).

## Phase 5 — Process Pooling for Heavy Initializers
- Design a configurable worker pool that pre-warms interactive applications with expensive startups.
- Define scheduling, concurrency limits, and idle eviction policies to prevent resource leaks.
- Implement request/response lifecycle: acquire worker, send input, await completion, recycle or restart worker on
  failure.
- Ensure safe shutdown semantics (graceful drain with optional force stop) and metrics for pool health.

## Scenario alignment — 2025-11-02 refresh
- **Completed coverage** — Single-run automation and interactive session scenarios are satisfied by Phases 3–4; keep
  defaults aligned with [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md) during
  maintenance.
- **Next focus after Phase 5** — Upcoming pooled client work (ICLI-016) must deliver both stateless service processors
  and stateful conversation workflows, including worker affinity, reset hooks, and diagnostics surfaced to Essential
  APIs.
- **Documentation outlook** — Phase 6 tasks remain responsible for scenario-driven guides (listen-only streaming, MCP
  adapters) so consumers can map APIs to catalogue entries quickly.
- **Release gate** — Phase 7 should retain the final scenario coverage audit to confirm single-run, interactive, pooled,
  and MCP workflows behave consistently across supported platforms.

## Phase 6 — Tooling, Observability, and Documentation
- Integrate logging/tracing hooks for debugging process interactions and pooling behavior.
- Document public API with Javadoc and guides: quick start, interactive sessions, pooling scenarios, troubleshooting.
- Provide code samples and migration notes for users coming from the legacy Kotlin library.
- Prepare release checklist and changelog.
- Produce scenario-driven guides, including CLI-backed MCP server adapters and listen-only streaming examples referenced
  in the catalogue, so documentation stays aligned with Essential API targets.

## Phase 7 — Hardening & Release
- Run stress and soak tests (large output, pool churn, rapid session restarts) and address bottlenecks.
- Validate cross-platform compatibility (Linux, macOS, Windows with ConPTY).
- Finalize versioning, publish artifacts, and hand off maintenance plan.
- Perform a final scenario coverage audit against the catalogue to confirm single-run, interactive, pooled, and MCP
  workflows behave consistently across supported platforms.
