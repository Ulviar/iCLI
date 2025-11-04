# ICLI-031 — Add pooled client integration coverage

## Status
- **Lifecycle stage:** Execution (paused pending ICLI-042)
- **Overall status:** Active
- **Last updated:** 2025-11-04
- **Owner:** Assistant

## Overview
- **Objective:** Exercise `ProcessPoolClient` against the real `ProcessPool` + `StandardProcessEngine` so pooled line
  workflows are validated end-to-end with deterministic fixtures.
- **Definition of Done:**
  1. Integration tests under `src/integrationTest` cover pooled stateless requests and conversation leases using real
  processes (e.g., `TestProcessCommand`).
  2. Multi-worker configurations are exercised (min/max > 1) to prove concurrent leases succeed and reset hooks run.
  3. Tests verify listener callbacks / diagnostics expectations that cannot be proven with the existing fakes.
  4. Gradle integration tests run green (`integrationTest` profile that includes the new suite) plus Spotless/SpotBugs
  as required by the session checklist.
  5. Dossier, backlog, and `.commit-message` updated; maintainer can trace verification evidence.
- **Constraints:** Use repository fixtures (no external dependencies), follow Kotlin + JUnit 6 testing conventions,
  respect PTY portability (stick to pipe-backed flows unless PTY coverage is essential), and keep runtime limits short
  to avoid flaky CI runs.
- **Roles to notify:** Maintainer

## Planning
- **Scope summary:** Add an integration suite for `ProcessPoolClient` that spins up pooled workers, drives both
  `ServiceProcessor` and `ServiceConversation`, and asserts multi-worker behaviour plus graceful shutdown.
- **Proposed deliverables:** New Kotlin integration tests + fixtures, updated backlog entry/link, refreshed dossier
  logs, and any documentation or comments needed to explain the integration scenario.
- **Open questions / risks:** Determine which `TestProcessCommand` mode best matches line processing without manual
  handshake (likely `--echo-stdin`); ensure tests remain deterministic on macOS/Linux/Windows; confirm integration test
  task already wired (or document command in execution history).
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-04.md](analysis/2025-11-04.md)
- **Knowledge consulted:** Recorded in the analysis log (architecture brief, process-pool spec, backlog, etc.).
- **Readiness decision:** Execution can begin once test process selection and concurrency strategy are confirmed (see
  analysis log for details).

## Research
- **Requests filed:** _None._
- **External outputs:** _Not applicable._
- **Summary:** _Pending._
- **Human response:** _Not applicable._

## Execution
- **History entries:**
  - [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** Added integration coverage for `ProcessPoolClient` (stateless processing, concurrent
  conversation + request, retirement replacement) using `StandardProcessEngine` and the shared test process fixture.
- **Testing:** `spotlessApply`, `integrationTest` (see execution log for run details).
- **Follow-up work:** Task paused on 2025-11-04 until ICLI-042 delivers the configurable process fixture needed for
  broader scenarios (multi-mode workloads, stress cases).
- **Retrospective:** Pending final review/close-out.

## Completion & archive
- **Archive status:** Active.
- **Archive location:** _TBD upon archive._
- **Final verification:** _Pending._

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** Test fixtures may need PTY coverage later if Essential pooled flows support PTY; out of scope for now but
  recorded as a watch item.
- **Links:** Related backlog items ICLI-032 (stress tests) and ICLI-033 (benchmarks).
