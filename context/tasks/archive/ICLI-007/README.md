# ICLI-007 — Assess Blocking vs Non-blocking Client API Strategy

- **Lifecycle stage:** Done
- **Overall status:** Archived
- **Last updated:** 2025-10-26
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Decide how iCLI exposes synchronous (blocking) and asynchronous (non-blocking) client workflows across CommandService, session APIs, and future pooling features.
- **Definition of Done:**
  1. Decision record captured inside the dossier (analysis/execution logs) with at least two viable strategies, their trade-offs, and migration guidance.
  2. Architecture brief updated with the chosen modality mix and integration touchpoints.
  3. Follow-up implementation tasks (if any) identified in backlog notes or task conclusions.
- **Constraints:** Align with Java 25 / Kotlin 2.2.20 stack, leverage virtual threads where practical, and respect Essential vs Advanced API layering described in the architecture brief.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Review existing API and runtime plans, evaluate candidate async surfaces (CompletableFuture, Flow/Channel adapters, listener callbacks), and select the default mix plus ergonomics for Essential and Advanced tiers.
- **Proposed deliverables:** Updated architecture brief, dossier log entry documenting the decision, refreshed backlog/notes if follow-up work appears.
- **Open questions / risks:** Need clarity on Kotlin coroutine integration expectations; must ensure async surface does not complicate diagnostics or timeout semantics; confirm how pooling clients inherit modality guarantees.
- **Backlog link:** [context/tasks/backlog.md](../backlog.md)

## Analysis
- **Log entries:** [`analysis/2025-10-26.md`](analysis/2025-10-26.md)
- **Knowledge consulted:** Roadmap, architecture brief, execution requirements & use case catalogue, execution engine benchmarks, and Kotlin legacy audit highlighted the need for dual modality support and informed evaluation criteria.
- **Readiness decision:** Ready for execution (design drafting can begin).

## Research
- **Requests filed:** None.
- **External outputs:** Not applicable.
- **Summary:** _TBD_
- **Human response:** Not applicable.

## Execution
- **History entries:** [`execution-history/2025-10-26.md`](execution-history/2025-10-26.md)
- **Implementation highlights:** Modality decision captured inside the dossier (analysis + execution logs), architecture brief updated with the ClientScheduler/async plan, backlog item ICLI-012 opened for implementation.
- **Testing:** Not run (documentation/design task only).
- **Follow-up work:** Implement ClientScheduler + async helpers (ICLI-012); align Flow/listen-only implementation with TBD-003.
- **Retrospective:** Logged in `execution-history/2025-10-26.md` (Definition of Done met; suggested automating dossier scaffolding).

## Completion & archive
- **Archive status:** Archived (2025-10-26)
- **Archive location:** `context/tasks/archive/ICLI-007/`
- **Final verification:** Reviewed design artifacts (architecture brief + dossier) with no code/tests required.

## Decisions & notes
- **Key decisions:** Adopt hybrid modality (blocking core + ClientScheduler) with async helpers on CommandService, LineSessionClient, and ProcessPoolClient plus Flow/Kotlin wrappers.
- **Risks:** Async helpers depend on ProcessEngine honoring interrupts; Kotlin coroutine adapters must avoid resource leaks.
- **Links:** Execution architecture brief.
