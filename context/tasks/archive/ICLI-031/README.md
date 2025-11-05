# ICLI-031 — Add pooled client integration coverage

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-05
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
  - [analysis/2025-11-05.md](analysis/2025-11-05.md)
- **Knowledge consulted:** Recorded in the analysis logs (architecture brief, process-pool spec, process fixture spec,
  backlog, etc.).
- **Readiness decision:** Execution resumed on 2025-11-05 following delivery of ICLI-042; scenarios enumerated in the
  latest analysis log.

## Research
- **Requests filed:** _None._
- **External outputs:** _Not applicable._
- **Summary:** _Pending._
- **Human response:** _Not applicable._

## Execution
- **History entries:**
  - [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
  - [execution-history/2025-11-05.md](execution-history/2025-11-05.md)
- **Implementation highlights:** Added comprehensive integration coverage for `ProcessPoolClient`, including stateless
  request reuse, concurrent leases, conversation retirement, process-fixture-powered streaming (pause/resume/STOP), and
  diagnostics verification for timeout-induced failures.
- **Testing:** `spotlessApply`, `integrationTest`, `test` (see execution logs for detailed runs).
- **Follow-up work:** _None._
- **Retrospective:** DoD satisfied—real ProcessPool integrations now cover success, concurrency, streaming, and failure
  paths, and the process fixture helpers unblock future suites.

## Completion & archive
- **Archive status:** Archived 2025-11-05.
- **Archive location:** `context/tasks/archive/ICLI-031/`.
- **Final verification:** `spotlessApply`, `integrationTest`, `test` (2025-11-05).

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** Test fixtures may need PTY coverage later if Essential pooled flows support PTY; out of scope for now but
  recorded as a watch item.
- **Links:** Related backlog items ICLI-032 (stress tests) and ICLI-033 (benchmarks).
