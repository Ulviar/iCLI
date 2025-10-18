# iCLI Command Execution Library — Roadmap

## Phase 1 — Foundations
- [x] Audit existing Kotlin solution to identify reusable ideas, pain points, and missing capabilities.
- [x] Establish repository guidelines, coding standards, and project conventions (formatting, testing, CI expectations).
  *Published in `context/guidelines/general/contributor-guidelines.md`.*

## Phase 2 — Discovery & Architecture
- Map functional requirements: single-shot execution, long-running interactive sessions, pooled interactive workers
  (see [execution requirements brief](execution-requirements.md)).
- Catalogue maintainer-sourced use cases (e.g., `mystem`, shells, REPLs) and non-functional constraints (cross-platform,
  resource usage).
- Research JVM ecosystem options for PTY/ConPTY (pty4j, Apache Commons Exec, JNA, Jansi) and decide on dependencies.
- Draft high-level architecture: process abstraction layer, session lifecycle, pooling strategy, error and timeout
model.
- Evaluate support for both blocking and non-blocking client APIs, ensuring the architecture can expose synchronous and
asynchronous workflows.
- Define data contracts (command specification, execution options, result types) and public API surface.

## Phase 3 — Core Execution Engine
- Implement command specification builder with explicit handling for shell vs direct execution and environment
overrides.
- Build single command executor: process launch, bounded output capture, exit status interpretation, timeout and
cancellation hooks.
- Add comprehensive error handling and diagnostics (logs, exception hierarchy, structured result objects).
- Create initial Kotlin-based integration tests (JUnit 6) covering success, failure, timeouts, large output, encoding
handling.

## Phase 4 — Interactive Session Support
- Implement interactive session API returning a handle with streaming IO and lifecycle controls suitable for headless
  PTY interactions.
- Support both pipe-based and PTY-backed sessions; abstract differences behind a unified interface.
- Provide expect-style helper utilities for scripted interactions and build sample usage documentation.
- Extend test coverage with simulated REPLs, echo servers, and PTY-required prompt workflows (visual TUIs remain out of
  scope).

## Phase 5 — Process Pooling for Heavy Initializers
- Design a configurable worker pool that pre-warms interactive applications with expensive startups.
- Define scheduling, concurrency limits, and idle eviction policies to prevent resource leaks.
- Implement request/response lifecycle: acquire worker, send input, await completion, recycle or restart worker on
failure.
- Ensure safe shutdown semantics (graceful drain with optional force stop) and metrics for pool health.

## Phase 6 — Tooling, Observability, and Documentation
- Integrate logging/tracing hooks for debugging process interactions and pooling behavior.
- Document public API with Javadoc and guides: quick start, interactive sessions, pooling scenarios, troubleshooting.
- Provide code samples and migration notes for users coming from the legacy Kotlin library.
- Prepare release checklist and changelog.

## Phase 7 — Hardening & Release
- Run stress and soak tests (large output, pool churn, rapid session restarts) and address bottlenecks.
- Validate cross-platform compatibility (Linux, macOS, Windows with ConPTY).
- Finalize versioning, publish artifacts, and hand off maintenance plan.
