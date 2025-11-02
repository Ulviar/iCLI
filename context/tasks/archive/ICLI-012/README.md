# ICLI-012 — Implement Client Async Scheduler & Helpers

## Status
- **Lifecycle stage:** Done
- **Overall status:** Archived
- **Last updated:** 2025-10-26
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Introduce the ClientScheduler abstraction plus async helpers (`runAsync`, `processAsync`) so
  CommandService, LineSessionClient, and future pool clients expose the non-blocking workflows defined in ICLI-007.
- **Definition of Done:**
  1. Production code adds ClientScheduler (virtual-thread default + injection hooks) and wires it through CommandService
  and LineSessionClient.
  2. New async APIs (`runAsync`, `processAsync`, etc.) return `CompletableFuture` with cancellation propagating
  interrupts to underlying tasks.
  3. Tests cover scheduler behaviour (including cancellation) and async helpers; Spotless/SpotBugs/test suites pass.
  4. Documentation (architecture brief or API docs) updated only if the public contract changes beyond what ICLI-007
  already captured.
- **Constraints:** Follow Java 25 + Kotlin 2.2.20 stack, honour `@NotNullByDefault`, keep async helpers aligned with the
  hybrid strategy recorded in EXPLANATION.md, avoid introducing non-UTF-8 text, and update dependencies responsibly.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Build a reusable ClientScheduler (virtual thread-backed) with cancel-aware futures, refactor
  CommandService and LineSessionClient to share it, and add async APIs; ensure tests prove cancellation and parity with
  blocking flows.
- **Proposed deliverables:** New Java classes (`ClientScheduler`, scheduler implementation), updated
  CommandService/LineSessionClient/InteractiveSessionClient code, dependency updates (if any), and corresponding unit
  tests plus execution logs.
- **Open questions / risks:** Need a clear strategy for managing scheduler lifecycle ownership; ensure async helpers do
  not deadlock when sharing scheduler with interactive sessions.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [analysis/2025-10-26.md](analysis/2025-10-26.md)
- **Knowledge consulted:** Architecture brief, EXPLANATION.md (ICLI-007 decision), coding standards, assistant notes,
  and existing client code/tests shaped the implementation plan.
- **Readiness decision:** Ready for execution.

## Research
- **Requests filed:** None.
- **External outputs:** Not applicable.
- **Summary:** _TBD_
- **Human response:** Not applicable.

## Execution
- **History entries:** [execution-history/2025-10-26.md](execution-history/2025-10-26.md)
- **Implementation highlights:** ClientScheduler + factory helpers and async `runAsync`/`processAsync` surfaced per the
  hybrid modality plan; Kotlin API support deferred to a future dedicated module.
- **Testing:** `./gradlew spotlessApply`, `./gradlew test` (see execution history for details).
- **Follow-up work:** ProcessPoolClient async variants (pending pool implementation), listen-only Flow helpers
  (TBD-003), and a new backlog item for a Kotlin API module.
- **Retrospective:** Logged in [execution-history/2025-10-26.md](execution-history/2025-10-26.md) (DoD satisfied;
  automate dossier scaffolding next time).

## Completion & archive
- **Archive status:** Archived (2025-10-26)
- **Archive location:** `context/tasks/archive/ICLI-012/`
- **Final verification:** Build formatting + `./gradlew test` successful on 2025-10-26.

## Decisions & notes
- **Key decisions:** Hybrid async strategy implemented via ClientScheduler + `CompletableFuture`; Kotlin API moved to a
  separate future module.
- **Risks:** Scheduler ownership vs caller-provided executors; future coroutine/Kotlin adapters must live outside the
  core artifact.
- **Links:** ICLI-007 dossier, execution architecture brief.
