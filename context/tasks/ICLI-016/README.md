# ICLI-016 — Add pooled request APIs & diagnostics

## Status
- **Lifecycle stage:** Planning
- **Overall status:** In Progress
- **Last updated:** 2025-11-02
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
- **Open questions / risks:** Balance stateless convenience against stateful session requirements, define how pooled
  helpers expose reset hooks/diagnostics, clarify lifecycle ownership (pool vs runner), and ensure API ergonomics across
  synchronous/asynchronous paths. Scenario coverage maintained in
  `[execution-use-case-catalogue.md](../../roadmap/execution-use-case-catalogue.md)`.
- **Backlog link:** `[context/tasks/backlog.md](../backlog.md)`

## Analysis
- **Log entries:**
  - `analysis/2025-11-02.md`
- **Knowledge consulted:** Referenced within dated analysis logs once created.
- **Readiness decision:** Pending deeper API design; execution not yet started.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:** None yet.
- **Implementation highlights:** Pending.
- **Testing:** Pending.
- **Follow-up work:** Pending.
- **Retrospective:** Pending.

## Completion & archive
- **Archive status:** Active.
- **Archive location:** To be determined when archived.
- **Final verification:** Pending.

## Decisions & notes
- **Key decisions:** Pending.
- **Risks:** Open questions listed in planning.
- **Links:** `[Process pool architecture](../../roadmap/process-pool-architecture.md)`
  `[Execution use case catalogue](../../roadmap/execution-use-case-catalogue.md)`
