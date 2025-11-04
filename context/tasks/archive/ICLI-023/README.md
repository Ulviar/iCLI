# ICLI-023 — Listen-only streaming helpers

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-04
- **Owner:** Assistant

## Overview
- **Objective:** Deliver Essential API helpers that let callers attach to long-running processes in a listen-only mode
  (tail/log follow) without manually wiring thread-based stream consumers.
- **Definition of Done:**
  - Add a dedicated client abstraction (and associated runner) that exposes stdout/stderr as streaming
  `Flow.Publisher<ByteBuffer>` instances and documents lifecycle + shutdown semantics.
  - Ensure helpers coexist with existing `InteractiveSessionClient`/`LineSessionClient` ergonomics, sharing
  `CommandCall` defaults and schedulers.
  - Provide diagnostics/transcript hook integration so listen-only consumers can opt into structured logging.
  - Cover behaviour with Kotlin tests (success, shutdown, error propagation) following TDD.
  - Update README + roadmap references to describe the new scenario support.
  - Keep `.commit-message` up to date and run `scripts/pre_response_checks.py` before handoff.
- **Constraints:** Adhere to Java 25 + Kotlin test stack, follow repository coding standards and Markdown formatting
  rules, and avoid introducing new dependencies without maintainer approval.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Extend `CommandService`/`PooledCommandService` with a `ListenOnlySessionRunner` that produces a new
  `ListenOnlySessionClient`. The client wraps `InteractiveSessionClient`, exposes stdout/stderr as single-subscriber
  `Flow.Publisher<ByteBuffer>` streams powered by a shared `InputStream` → publisher adapter, and forwards lifecycle
  controls (onExit, signals, close). Kotlin/Flow adapters will be delivered in a separate module so the core project
  stays Java-only.
- **Proposed deliverables:** Java source for the listen-only runner/client plus publisher implementation, pooled
  counterparts (`PooledListenOnlySessionRunner`/`PooledListenOnlyConversation`), Kotlin tests covering the new helpers,
  README/architecture/backlog updates (calling out the external Flow module), refreshed `.commit-message`, and dossier
  logs.
- **Open questions / risks:** Tweak chunk sizing/backpressure defaults for the publisher (start with 4 KiB buffers,
  allow future tuning); ensure publisher cancellation closes readers without terminating pooled workers (introduce
  non-owning view factory); document that diagnostics listeners remain the transcript hook for streaming captures and
  that Flow-specific adapters live outside this module.
- **Backlog link:** [context/tasks/backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-04.md](analysis/2025-11-04.md)
- **Knowledge consulted:** Documented in the analysis log (governance docs, roadmap, catalogue, knowledge base,
  research, README/backlog).
- **Readiness decision:** Ready to prototype an API design pending clarifications on publisher/backpressure semantics
  captured in the analysis log.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:** [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** Listen-only session runners/clients (standard + pooled), publisher plumbing, and
  supporting fixtures/tests added; docs/backlog updated to reflect the new helper availability and Kotlin Flow deferral.
- **Testing:** `./gradlew test` (2025-11-04) — passed.
- **Follow-up work:** Publish the external Kotlin Flow adapter module; monitor publisher tuning/backpressure options.
- **Retrospective:** Captured in [execution-history/2025-11-04.md](execution-history/2025-11-04.md).

## Completion & archive
- **Archive status:** Archived (2025-11-04)
- **Archive location:** `context/tasks/archive/ICLI-023/`
- **Final verification:** `./gradlew test` (2025-11-04)

## Decisions & notes
- **Key decisions:** Keep listen-only helpers Java-only (Flow adapters live in an external module); expose both owning
  and shared listen-only clients so pooled workers can keep running after subscribers detach; cap chunk size at 4 KiB
  for the initial publisher implementation.
- **Risks:** Follow-up module for Kotlin Flow adapters still outstanding; chunk size/backpressure tuning may need more
  configurability once real workloads land.
- **Links:** [README.md](/context/README.md),
  [context/roadmap/execution-architecture-brief.md](../roadmap/execution-architecture-brief.md)
