# ICLI-026 — Draft pooled command service design

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-03
- **Owner:** Assistant

## Overview
- **Objective:** Produce a design that introduces `PooledCommandService` and pooled runner abstractions while outlining
  shared execution collaborators and the migration path from `CommandService.pooled()`.
- **Definition of Done:**
  - Draft a design note under the ICLI-016 dossier capturing `PooledCommandService`, pooled runner types, and shared
  execution templates.
  - Describe diagnostics/listener wiring plus package placement decisions, informed by the 2025-11-03 analysis baseline.
  - Specify compatibility strategy for existing `CommandService.pooled()` callers and follow-up tasks
  (ICLI-027–ICLI-030).
  - Update this dossier and backlog metadata to reflect the delivered design artefact.
- **Constraints:** Align with the execution architecture brief, process pool architecture spec, and maintain Essential
  API ergonomics without leaking advanced runtime concerns.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Capture the target API surface for pooled command services, including runner responsibilities,
  shared execution templates, and diagnostics listener composition.
- **Proposed deliverables:** Design note within the ICLI-016 dossier, updated roadmap/backlog references, refreshed
  dossier entries (analysis, planning), and an initial follow-up task checklist.
- **Open questions / risks:** Package layout for pooled services, listener override strategy, and ensuring the migration
  path for `CommandService.pooled()` avoids breaking existing clients.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-03.md](analysis/2025-11-03.md)
- **Knowledge consulted:** The analysis log captures insights from the backlog entry, ICLI-016 design history, and
  roadmap specifications that shape the proposal.
- **Readiness decision:** Ready to author the design note; no further research blockers.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:** None yet.
- **Implementation highlights:** Pending design authoring.
- **Testing:** Not applicable for design-only task.
- **Follow-up work:** To be captured after the design lands.
- **Retrospective:** Pending completion.

## Completion & archive
- **Archive status:** Active.
- **Archive location:** To be determined upon completion.
- **Final verification:** Pending.

## Decisions & notes
- **Key decisions:** Design captured in [pooled command service note](design/2025-11-03-pooled-command-service.md).
- **Risks:** Design must avoid regressing existing `ProcessPoolClient` scenarios during migration.
- **Links:** Related backlog items ICLI-027–ICLI-030 will depend on this design.
