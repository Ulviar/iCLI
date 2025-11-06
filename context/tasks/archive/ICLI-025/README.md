# ICLI-025 — Add pooled conversation affinity features

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-06
- **Owner:** Assistant

## Overview
- **Objective:** Extend pooled client APIs so stateful conversations can express worker affinity, explicit reset
  semantics, and health signalling aligned with catalogue scenarios.
- **Definition of Done:**
  1. Essential pooled APIs accept optional affinity metadata when opening or reacquiring conversations, ensuring workers
  can be reused without holding leases indefinitely.
  2. Callers can request explicit reset/retirement with scoped diagnostics so unhealthy workers are quarantined and
  their retirement reason is observable.
  3. Documentation (README, roadmap references) reflects the new affinity feature set and conversation lifecycle hooks.
  4. Kotlin + JUnit 6 coverage verifies affinity workflows, reset signalling, and listener callbacks.
  5. `.commit-message` summarises the diff and required Gradle/Spotless gates run cleanly.
- **Constraints:** Follow coding standards, nullability policy, and TDD; wire diagnostics through existing
  `ServiceProcessorListener`/`PoolDiagnosticsListener` infrastructure without introducing new dependencies; keep public
  API naming consistent with Essential client conventions.
- **Roles to notify:** Maintainer

## Planning
- **Scope summary:** Design and implement affinity-aware `ServiceConversation` APIs plus pooled runner integrations,
  ensuring callers can pin or reacquire workers, trigger resets/retirements intentionally, and observe health signals.
- **Proposed deliverables:** Updated Java APIs, Kotlin tests, README/roadmap updates, refreshed `.commit-message`,
  execution logs capturing validation steps.
- **Open questions / risks:** How to balance affinity stickiness with pool fairness; whether affinity bookkeeping lives
  inside `ProcessPoolClient` or `ProcessPool`; how diagnostics should surface affinity cache hits/misses; confirm that
  listener hooks cover reset/retire outcomes without duplicating pool events.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-05.md](analysis/2025-11-05.md)
- **Knowledge consulted:** execution-use-case catalogue, process-pool architecture brief, execution architecture brief,
  ICLI-016 dossier (see log for details).
- **Readiness decision:** Ready to proceed once affinity API shape is finalised (see gaps in analysis log).

## Research
- **Requests filed:** _None._
- **External outputs:** _N/A._
- **Summary:** _Pending._
- **Human response:** _Pending._

## Execution
- **History entries:** [execution-history/2025-11-06.md](execution-history/2025-11-06.md)
- **Implementation highlights:** PreferredWorker introduced across pool APIs; pooled value objects documented/tested; CloseDirective enforces conversation close invariants; ConversationAffinityRegistry is now a sealed interface with clear enabled/disabled implementations.
- **Testing:** `./gradlew spotlessApply`; `./gradlew test`.
- **Follow-up work:** None.
- **Retrospective:** Definition of Done satisfied—affinity metadata, reset/retirement semantics, docs, and coverage delivered; future tasks should seed execution logs earlier to reduce close-out effort.

## Completion & archive
- **Archive status:** Archived (moved 2025-11-06)
- **Archive location:** [context/tasks/archive/ICLI-025/](/context/tasks/archive/ICLI-025)
- **Final verification:** Suite green via `./gradlew test`; formatting clean via `./gradlew spotlessApply`.

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** _Pending._
- **Links:** [README.md](/README.md),
  [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md)
