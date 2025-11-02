# ICLI-010 — Implement streaming OutputCapture and diagnostics hooks

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-10-28
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Enable streaming output capture with diagnostics in the core runtime so long-running commands can emit
  bounded summaries while clients subscribe to live events.
- **Definition of Done:**
  - Implement a streaming-capable output sink and wire it through `StandardProcessEngine` without raising
  `UnsupportedOperationException`.
  - Emit diagnostics/truncation events as described in the execution architecture brief and surface them through
  appropriate runtime hooks.
  - Extend unit and integration coverage to validate streaming behaviour, truncation flags, and diagnostics publication.
  - Update relevant documentation (architecture brief, knowledge base, or README snippets) to describe streaming capture
  usage and defaults.
  - Pass formatting, SpotBugs, and Gradle test suites with execution history recorded.
- **Constraints:** Follow repository coding standards (Java 25, Spotless, JetBrains annotations), adhere to TDD, and
  maintain compatibility with existing bounded/ discard policies.
- **Roles to notify:** Maintainer (per [project-roles.md](../../../guidelines/icli/project-roles.md)).

## Planning
- **Scope summary:** Replace the placeholder streaming implementation with functional runtime support, build diagnostics
  hooks, and expose the behaviour through tests and documentation.
- **Proposed deliverables:** Runtime code updates (`OutputSinkFactory`, streaming sink implementation, diagnostics bus),
  revised client/runtime tests, dossier logs, updated docs, and refreshed backlog status.
- **Open questions / risks:** How diagnostics should be surfaced to callers (new event bus vs extending existing APIs);
  defining deterministic tests for streaming capture without flakiness; ensuring no regressions in bounded capture
  performance.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-10-28.md](analysis/2025-10-28.md)
- **Knowledge consulted:** See [analysis/2025-10-28.md](analysis/2025-10-28.md).
- **Readiness decision:** Proceeding with execution; design outline captured on 2025-10-28.

## Research
- **Requests filed:** None.
- **External outputs:** _N/A_
- **Summary:** _N/A_
- **Human response:** _N/A_

## Execution
- **History entries:**
  - [execution-history/2025-10-28.md](execution-history/2025-10-28.md)
- **Implementation highlights:** Diagnostics listener + streaming sink support landed per 2025-10-28 log.
- **Testing:** `gradle test`; `gradle spotlessApply`; `python scripts/pre_response_checks.py`
- **Follow-up work:** Grow diagnostics into a full bus with additional event types (launch, exit, timeout).
- **Retrospective:** Streaming capture is now production-ready with diagnostics coverage; downstream work focuses on
  expanding the diagnostics surface area and long-run performance validation.

## Completion & archive
- **Archive status:** Archived (2025-10-28)
- **Archive location:** `context/tasks/archive/ICLI-010/`
- **Final verification:** `gradle test`; `gradle spotlessApply`; `python scripts/pre_response_checks.py`

## Decisions & notes
- **Key decisions:** Introduced `DiagnosticsListener` as the interim diagnostics surface and threaded it through
  `ExecutionOptions` so callers opt in without new entry points.
- **Risks:** Diagnostics delivery remains synchronous/on-drain threads; high-volume listeners should migrate to the
  planned diagnostics bus (tracked separately) to avoid back-pressure.
- **Links:** [context/roadmap/execution-architecture-brief.md](../../../roadmap/execution-architecture-brief.md);
  [backlog.md](/context/tasks/backlog.md)
