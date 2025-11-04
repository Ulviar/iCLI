# ICLI-042 — Build configurable process fixture module

## Status
- **Lifecycle stage:** Planning
- **Overall status:** Backlog
- **Last updated:** 2025-11-04
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
- **Log entries:** _Pending._
- **Knowledge consulted:** _Pending._
- **Readiness decision:** _Pending._

## Research
- **Requests filed:** Link to logs under `research/requests/` (delete if none). Assistants hand off research to humans.
- **External outputs:** Reference files stored in `context/research/`.
- **Summary:** Highlight conclusions or data points that influenced the plan.
- **Human response:** Capture who responded, the decision, and any follow-up guidance.

## Execution
- **History entries:** Link to dated files under `execution-history/`.
- **Implementation highlights:** Capture major code or documentation changes.
- **Testing:** List verification steps (commands, tools, environments).
- **Follow-up work:** Record any issues deferred to future tasks.
- **Retrospective:** Reference the execution history entry that contains the goal/DoD confirmation and process
  improvement suggestions.

## Completion & archive
- **Archive status:** Active.
- **Archive location:** _TBD upon completion._
- **Final verification:** _Pending._

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** Ensure fixture output patterns do not make tests flaky across platforms; keep resource usage modest to
  avoid slowing CI.
- **Links:** [Process fixture spec](/context/roadmap/process-fixture-spec.md)
