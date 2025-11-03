# ICLI-016 — Add pooled request APIs & diagnostics

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-03
- **Owner:** Assistant

## Overview
- **Objective:** Design and deliver service-level request APIs atop the existing `ProcessPool`, wiring diagnostics,
  timeout, and reset hook behaviour into the client surface.
- **Definition of Done:**
  - Expose pooled request helpers (e.g., `ProcessPoolClient`, `ServiceProcessor`) that coordinate with `ClientScheduler`
  and `ProcessPool` to run stateless workloads.
  - Surface per-request timeout, diagnostics, and transcript reset integration consistent with pool runtime semantics.
  - Provide Kotlin + JUnit 6 coverage that exercises pooled request flows, diagnostics propagation, and failure modes.
  - Update docs (README, architecture briefs) and maintain a refreshed `.commit-message`.
- **Constraints:** Follow repository coding standards, reuse `ProcessPool` runtime primitives, honour TDD, keep Gradle
  tooling/Spotless/SpotBugs passing, and coordinate configuration defaults with existing ExecutionOptions.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Extend the client layer with pooled request abstractions that hide lease management while giving
  callers access to pool handles and diagnostics when needed.
- **Proposed deliverables:** Production Java APIs, Kotlin-based tests, documentation updates (architecture brief,
  README), refreshed `.commit-message`, and updated dossier logs.
- **Design snapshot (2025-11-02):**
  - Add `ProcessPoolClient` as the Essential facade that owns a `ProcessPool`, exposes lifecycle helpers, and hands out
  service-level entry points.
  - Introduce `ServiceProcessor` for stateless request/response flows using pooled `LineSessionClient` semantics with
  per-request overrides, async variants backed by `ClientScheduler`, and automatic reset when the lease returns.
  - Provide conversation-oriented handles (working name: `ServiceConversation`) that keep a lease open across multiple
  exchanges, expose both `LineSessionClient` and `InteractiveSessionClient` ergonomics, and allow explicit reset or
  retirement before returning to the pool.
  - Emit service-level diagnostics via a lightweight `ServiceProcessorListener` that decorates `PoolDiagnosticsListener`
  events with request IDs, durations, timeout outcomes, and reset failures.
  - Allow optional affinity keys when opening conversations so callers can re-acquire the same worker without holding a
  lease indefinitely; scope to a best-effort cache layered atop pool acquisitions.
  - Refactor the facade into dedicated collaborators (`ProcessPoolClient`, `ServiceProcessor`, `ServiceConversation`) so
  invariants are enforced without exposing internals and each class carries focused unit tests.
- **Refinement goals:** remove redundant `null` checks outside `@Nullable` contracts and add single-responsibility unit
  tests for the new collaborators while keeping integration coverage through `ProcessPoolClientTest`.
- **Open questions / risks:** Balance stateless convenience against stateful session requirements, define how pooled
  helpers expose reset hooks/diagnostics, clarify lifecycle ownership (pool vs runner), and ensure API ergonomics across
  synchronous/asynchronous paths. Scenario coverage maintained in
  [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md).
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-02.md](analysis/2025-11-02.md)
  - [analysis/2025-11-03.md](analysis/2025-11-03.md)
- **Knowledge consulted:** Architecture brief, process pool specification, and research notes cited within each log.
- **Readiness decision:** Completed; design validated and split into follow-up tasks where scope exceeded this effort.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:**
  - [execution-history/2025-11-02.md](execution-history/2025-11-02.md)
  - [execution-history/2025-11-03.md](execution-history/2025-11-03.md)
- **Implementation highlights:** Delivered `ProcessPoolClient`, `ServiceProcessor`, and `ServiceConversation` as
  standalone collaborators with diagnostics listener support; documented contracts and updated roadmap entries; removed
  redundant null guards.
- **Testing:** Added focused unit suites plus expanded pooled client tests; `gradle test` and `spotlessApply` executed
  successfully via MCP tooling.
- **Follow-up work:** Integration coverage, multi-worker stress suites, and JMH benchmarking scheduled under
  ICLI-031–ICLI-033.
- **Retrospective:** API contracts and documentation now align; separating pooled helpers improved modularity while
  listener hooks remain observable through new tests.

## Completion & archive
- **Archive status:** Archived (2025-11-03).
- **Archive location:** [context/tasks/archive/ICLI-016/README.md](README.md)
- **Final verification:** Definition of Done satisfied; see execution history for command logs.

## Decisions & notes
- **Key decisions:** Finalised Essential API split into `ProcessPoolClient`, `ServiceProcessor`, and
  `ServiceConversation`; listener callbacks treated as part of the public contract; future pooled runner simplification
  delegated to ICLI-026–ICLI-030.
- **Risks:** Integration and performance validation outstanding; tracked via ICLI-031–ICLI-033.
- **Links:** [Process pool architecture](/context/roadmap/process-pool-architecture.md) [Execution use case
  catalogue](/context/roadmap/execution-use-case-catalogue.md) [Pooled command service design
  (ICLI-026)](../../ICLI-026/design/2025-11-03-pooled-command-service.md)
