# ICLI-028 — Implement PooledCommandService facade

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-03
- **Owner:** Assistant

## Overview
- **Objective:** Introduce `PooledCommandService` and pooled runner facades so Essential API users can access the
  process pool without dropping to `ProcessPoolClient`.
- **Definition of Done:**
  - `PooledCommandService` builds and owns a single `ProcessPoolClient`, mirroring existing `CommandService` defaults.
  - New pooled runners (command, line session, interactive) reuse the shared runner core and surface async helpers.
  - Kotlin tests cover pooled service construction, runner behaviour, and legacy compatibility for
  `CommandService.pooled()`.
  - Documentation and backlog/dossier updates describe the new facade and its relationship to existing APIs.
- **Constraints:** Follow repository coding standards, TDD discipline, Spotless/SpotBugs gates, and reuse shared runner
  abstractions delivered in ICLI-027 without breaking binary compatibility.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Implement the pooled service facade, wire pooled runners through shared helpers, and ensure
  existing clients continue to function via `CommandService.pooled()`.
- **Proposed deliverables:** Java implementation for `PooledCommandService` plus pooled runner types, supporting Kotlin
  tests, updated documentation (dossier/backlog notes, README if required), and refreshed `.commit-message`.
- **Open questions / risks:** Determine how pooled runners expose async helpers without duplicating scheduler logic;
  confirm listener plumbing and pool lifecycle semantics align with design; validate compatibility with JPMS exports.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-03.md](analysis/2025-11-03.md)
- **Knowledge consulted:** See linked analysis log for design notes, roadmap references, and prior task outputs.
- **Readiness decision:** Ready to begin execution using shared runner abstractions and the ICLI-026 facade design.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:**
  - [execution-history/2025-11-03.md](execution-history/2025-11-03.md)
- **Implementation highlights:** Delivered the pooled Essential facade (`PooledCommandService`) plus runner wrappers,
  extended `ProcessPoolClient`/`ResponseDecoder` plumbing, extracted pooled conversation types to satisfy the single
  public-class rule, and refreshed Kotlin coverage for pooled workflows including async and custom-decoder scenarios.
- **Testing:** `spotlessApply`, `gradle test` (via MCP `run_gradle_tests`).
- **Follow-up work:** Captured post-task ideas in [plan.md](plan.md) for potential future refactors.
- **Retrospective:** Definition of Done met. Essential users now access pooled helpers without dropping to
  `ProcessPoolClient`, async/listener behaviour remains consistent, and manual validation sequences (architecture,
  documentation, nullability, test coverage, modern API, code smell cleanup) completed with no outstanding findings.

## Completion & archive
- **Archive status:** Archived 2025-11-03.
- **Archive location:** [context/tasks/archive/ICLI-028/](/context/tasks/archive/ICLI-028).
- **Final verification:** `spotlessApply`, `gradle test`, `scripts/pre_response_checks.py`.

## Decisions & notes
- **Key decisions:** None yet.
- **Risks:** Potential lease leaks or duplicated scheduling logic if pooled runners do not reuse shared helpers
  correctly.
- **Links:** -
  [context/tasks/archive/ICLI-026/design/2025-11-03-pooled-command-service.md](../ICLI-026/design/2025-11-03-pooled-command-service.md)
  - [context/tasks/archive/ICLI-027/README.md](../ICLI-027/README.md)
