# ICLI-042 — Build configurable process fixture module

## Status
- **Lifecycle stage:** Done
- **Overall status:** Archived
- **Last updated:** 2025-11-05
- **Owner:** Assistant

## Overview
- **Objective:** Implement the standalone program specified in
  [process-fixture-spec.md](/context/roadmap/process-fixture-spec.md) so iCLI can run integration/stress tests against
  realistic single-run, line, and streaming workloads.
- **Definition of Done:**
  1. New Gradle module (e.g., `process-fixture`) containing the CLI program plus README/AGENTS per repo standards.
  2. Program supports startup delay, per-request runtime bounds, payload sizing, streaming profiles, and failure
  injection as defined in the spec.
  3. Provides line-mode (`READY` handshake) and streaming-mode behaviours compatible with existing
  `LineSessionClient`/listen-only tests.
  4. Includes automated tests (unit + smoke) proving each mode works; wire into build so other modules can depend on the
  fixture.
  5. Documentation updated (spec references, module README); `.commit-message` refreshed and required Gradle tasks run.
- **Constraints:** Java 25 for production code, Kotlin + JUnit 6 for tests, follow AGENTS/coding standards, reuse
  repository tooling (Spotless, SpotBugs). Keep outputs deterministic when seeded to avoid flaky tests.
- **Roles to notify:** Maintainer

## Planning
- **Scope summary:** Create a reusable process fixture (binary + helper scripts) offering configurable modes
  (single/line/stream) with runtime controls so integration and performance tests can target realistic workflows.
- **Proposed deliverables:** New module (code, README, AGENTS), CLI entry point, Kotlin smoke tests, Gradle wiring so
  other modules can reference the fixture, updated context docs if behaviour evolves.
- **Open questions / risks:** Need to confirm module placement (root vs samples), determine acceptable level of
  randomness (seed defaults), and ensure streaming mode can run on Windows + Unix without PTY assumptions.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [2025-11-04](analysis/2025-11-04.md)
- **Knowledge consulted:** [AGENTS.md](/AGENTS.md), [Process fixture spec](/context/roadmap/process-fixture-spec.md),
  [Coding standards](/context/guidelines/coding/standards.md), [Assistant
  notes](/context/guidelines/icli/assistant-notes.md), [Testing strategy](/context/testing/strategy.md)
- **Readiness decision:** Ready for execution — requirements and build touchpoints understood; scope controlled via
  extensible CLI design.

## Research
- **Requests filed:** Link to logs under `research/requests/` (delete if none). Assistants hand off research to humans.
- **External outputs:** Reference files stored in `context/research/`.
- **Summary:** Highlight conclusions or data points that influenced the plan.
- **Human response:** Capture who responded, the decision, and any follow-up guidance.

## Execution
- **History entries:** [2025-11-04](execution-history/2025-11-04.md)
- **Implementation highlights:** Added `:process-fixture` module with the CLI/runtime, controllers for all modes, helper
  utilities, and documentation; root modules now depend on the fixture for tests.
- **Testing:** See execution log for `:process-fixture:test`, filtered `PoolStateTest`, and full `test` task runs.
- **Follow-up work:** Track future enhancements (listen-only streaming fixtures, richer JSON parsing) as new backlog
  items if required.
- **Retrospective:** Covered inside the 2025-11-04 execution history entry.

## Completion & archive
- **Archive status:** Archived (2025-11-05).
- **Archive location:** `context/tasks/archive/ICLI-042/`.
- **Final verification:** `:process-fixture:test` plus full `test` rerun passed prior to archiving (see execution log).

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** Ensure fixture output patterns do not make tests flaky across platforms; keep resource usage modest to
  avoid slowing CI.
- **Links:** [Process fixture spec](/context/roadmap/process-fixture-spec.md)
