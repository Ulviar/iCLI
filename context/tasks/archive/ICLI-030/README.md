# ICLI-030 — Finalise pooled API cleanup & migration

## Status
- **Lifecycle stage:** Done
- **Overall status:** Completed
- **Last updated:** 2025-11-04
- **Owner:** Assistant

## Overview
- **Objective:** Complete the pooled client migration by relocating advanced helpers (`ProcessPoolClient`,
  `ServiceProcessor`, `ServiceConversation`, `ServiceProcessorListener`) under `com.github.ulviar.icli.client.pooled`,
  clarifying diagnostics/listener wiring, and publishing migration guidance for consumers.
- **Definition of Done:**
  - Advanced pooled helpers live in `com.github.ulviar.icli.client.pooled` with updated JPMS exports, package info, and
  backward-compatible public APIs.
  - `CommandService` surface and Javadoc clearly direct callers to the new pooled facade while keeping a documented path
  to `ProcessPoolClient` for advanced scenarios.
  - Documentation (README + roadmap/design references) explains the migration and Essential vs advanced entry points.
  - Kotlin unit tests covering pooled helpers pass after package moves, and Spotless/SpotBugs/test suites stay green.
  - `.commit-message` and dossier/logs reflect the delivered work.
- **Constraints:** Maintain binary compatibility (no removals), follow repository coding standards/TDD, run Gradle tasks
  via MCP tools only, and keep instructions in AGENTS/context docs up to date if behaviour changes.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Repackage pooled advanced helpers under `client.pooled`, update CommandService + pooled facade
  wiring, and refresh docs/tests so consumers understand the new default surface without losing advanced capabilities.
- **Proposed deliverables:** Java refactor (package moves + Javadoc), Kotlin test updates, README/roadmap adjustments,
  refreshed `.commit-message`, updated dossier entries/logs.
- **Open questions / risks:** Whether to rename `CommandService.pooled()` now that it returns the facade versus simply
  updating docs; best home for migration guidance (README vs roadmap/architecture brief); risk of JPMS regressions when
  moving public types.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-04.md](analysis/2025-11-04.md)
- **Knowledge consulted:** See the linked analysis log for design/backlog/roadmap sources that informed the plan.
- **Readiness decision:** Ready for execution once documentation placement is finalised during implementation.

## Research
- **Requests filed:** Link to logs under `research/requests/` (delete if none). Assistants hand off research to humans.
- **External outputs:** Reference files stored in `context/research/`.
- **Summary:** Highlight conclusions or data points that influenced the plan.
- **Human response:** Capture who responded, the decision, and any follow-up guidance.

## Execution
- **History entries:**
  - [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** Capture major code or documentation changes.
- **Testing:** List verification steps (commands, tools, environments).
- **Follow-up work:** Record any issues deferred to future tasks.
- **Retrospective:** Reference the execution history entry that contains the goal/DoD confirmation and process
  improvement suggestions.

## Completion & archive
- **Archive status:** Archived 2025-11-04.
- **Archive location:** context/tasks/archive/ICLI-030/README.md (this file)
- **Final verification:** `spotlessApply` + `test` executed via MCP on 2025-11-04; `scripts/pre_response_checks.py` run
  before handoff.

## Decisions & notes
- **Key decisions:** Bullet points describing irreversible choices.
- **Risks:** Outstanding concerns that need monitoring.
- **Links:** Related tasks, pull requests, or specs.
