# ICLI-004 — Outline Process Execution Architecture

## Status
- **Lifecycle stage:** Done
- **Overall status:** Archived
- **Last updated:** 2025-10-24
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Draft the high-level architecture for the iCLI execution engine covering single-run, interactive, and
  pooled workflows.
- **Definition of Done:** Architecture brief added to `context/roadmap/`; component responsibilities and integration
  points documented; dependencies or open design questions logged in the dossier.
- **Constraints:** Align with the execution requirements brief, knowledge base guidance, and Java 25 / Kotlin 2.2.20
  stack.
- **Roles to notify:** Maintainer (project owner).

## Planning
- **Scope summary:** Define key modules (command spec, execution engine, session handling, pooling) and their
  interactions, including PTY integration strategy.
- **Proposed deliverables:** Architecture document; updated roadmap or diagrams; list of follow-up implementation
  tasks.
- **Open questions / risks:** Pending use case catalogue may surface new constraints; PTY library selection could
  impact design choices.
- **Backlog link:** [context/tasks/backlog.md](../backlog.md)

## Analysis
- **Log entries:** 2025-10-18 — Reviewed requirements, use case catalogue, knowledge base, and research benchmarks to
  frame architecture scope.
- **Knowledge consulted:**
  - [Execution requirements][req]
  - [Execution use case catalogue][usecases]
  - [Java Terminal & Process Integration — Knowledge Base][kb-pty]
  - [iCLI execution engine benchmarks][bench]
  - [Kotlin legacy solution audit][audit]

[req]: ../../roadmap/execution-requirements.md
[usecases]: ../../roadmap/execution-use-case-catalogue.md
[kb-pty]: ../../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md
[bench]: ../../research/icli-execution-engine-benchmarks.md
[audit]: ../../research/experiments/kotlin-solution-audit.md
- **Readiness decision:** Ready for architecture drafting; no blockers identified.

## Research
- **Requests filed:** None.
- **External outputs:** Not applicable.
- **Summary:** No additional research required at this stage; open items logged in the architecture brief with planned
  handling.
- **Human response:** Not applicable.

- **History entries:**
  - 2025-10-18 — Authored execution architecture brief detailing modules, responsibilities, and integration points;
    incorporated maintainer feedback emphasising reliability-first priorities and a simplified API layer while scoping
    out TUIs.
  - 2025-10-23 — Reconciled current implementation against the architecture brief, captured gaps (Essential defaults,
    session synchronisation, follow-up backlog items), then executed the API rename aligning code/tests/docs around the
    new `core` and `client` packages.
  - 2025-10-24 — Finished Essential client surface refactor (builder snapshots + streamlined overloads), simplified
    `LineSessionClient`, documented decoder/interactive considerations, reviewed `core` package scope, and queued
    follow-up backlog items for defaults and streaming helpers.
- **Implementation highlights:** Added `context/roadmap/execution-architecture-brief.md` outlining API, runtime,
  session, and pooling layers with cross-cutting policies; clarified Essential API facades (simple service pooling plus
  optional batch), documented default behaviours (timeouts, capture limits, retries, exception types), and sketched
  method signatures/configuration points alongside the advanced API; implemented the foundational `CommandDefinition`,
  `ShellConfiguration`, `ExecutionOptions`, `OutputCapture`, and shutdown primitives with Kotlin unit tests via TDD
  while broader runtime remains pending; adopted JetBrains `@NotNullByDefault`/`@Nullable` annotations and removed
  redundant null guards to align with the new guideline; updated open-question handling.
- **Testing:** Not applicable.
- **Follow-up work:** Draft implementation tasks for single-run executor, session manager, worker pool, Essential
  service pool facade, optional batch processor, and Essential defaults registry; prototype PTY integration.

## Completion & archive
- **Archive status:** Archived (2025-10-24).
- **Archive location:** `context/tasks/archive/ICLI-004/`.
- **Final verification:** Architecture brief, client/core alignment, docs/tests updates, and backlog follow-ups reviewed prior to archiving.

## Decisions & notes
- **Key decisions:** Adopt layered architecture sharing runtime primitives across single-run, session, and pooled
  workflows; introduce dual API tiers (Essential vs Advanced) to balance simplicity with configurability; provide a
  simple service pool API for one-in/one-out integrations while keeping lease-driven pooling for advanced use; keep
  TUI rendering capabilities out of scope.
- **Risks:** Architecture may depend on outcomes from ICLI-003 and PTY library evaluation.
- **Links:** [Execution architecture brief](../../roadmap/execution-architecture-brief.md)
