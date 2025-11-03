# ICLI-027 — Extract shared runner execution core

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-03
- **Owner:** Assistant

## Overview
- **Objective:** Refactor the client runners to share a common execution core that standard and pooled services can
  reuse without diverging behaviour.
- **Definition of Done:**
  - Introduce shared abstractions (e.g., runner defaults, command call factory, session/line factories) referenced by
  existing `CommandRunner`, `LineSessionRunner`, and `InteractiveSessionRunner`.
  - Preserve current public behaviour and API signatures, confirmed via tests.
  - Add or update Kotlin/JUnit coverage protecting the shared layer and existing runner flows.
  - Update documentation/dossiers to record the refactor and any follow-up items.
- **Constraints:** Follow repository coding standards, adhere to TDD, route Gradle tasks through MCP tools, and keep
  compatibility with the `PooledCommandService` design produced in ICLI-026.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Extract reusable runner-building collaborators and rewire existing runners to rely on them,
  preparing for pooled runner adoption.
- **Proposed deliverables:** Shared runner core classes, refactored runners, corresponding tests, updated docs/dossier
  entries, refreshed `.commit-message`.
- **Implementation approach:** Introduce `com.github.ulviar.icli.client.internal.runner` with `RunnerDefaults`,
  `CommandCallFactory`, `SessionLauncher`, and `LineSessionFactory`; adjust current runners and
  `InteractiveSessionStarter` to depend on these helpers; add Kotlin unit tests covering the new helpers and regression
  coverage for existing runners, including PTY fallback behaviour.
- **Open questions / risks:** How to stage refactor without breaking binary compatibility; ensuring pooled tasks can
  compose the same helpers; integration with existing listener/decoder defaults; confirm JPMS visibility for internal
  helpers in tests.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-03.md](analysis/2025-11-03.md)
- **Knowledge consulted:** Recorded in the analysis log; references ICLI-026 design note, backlog entry, and prior
  analysis.
- **Readiness decision:** Execution underway.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:** [execution-history/2025-11-03.md](execution-history/2025-11-03.md)
- **Implementation highlights:** Shared runner helpers extracted and wired into existing runners.
- **Testing:** `gradle test` via MCP `run_gradle_tests`.
- **Follow-up work:** None identified beyond downstream pooled runner tasks.
- **Retrospective:** Captured in the latest execution history entry.

## Completion & archive
- **Archive status:** Active.
- **Archive location:** To be determined upon completion.
- **Final verification:** Pending.

## Decisions & notes
- **Key decisions:** None yet.
- **Risks:** Regression potential during refactor; need to verify integration with future pooled runners.
- **Links:** [ICLI-026 design note](../archive/ICLI-016/design/2025-11-03-pooled-command-service.md)
