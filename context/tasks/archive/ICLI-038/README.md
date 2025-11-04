# ICLI-038 — Author scenario harness & docs for single-run comparisons

## Status
- **Lifecycle stage:** Retrospective
- **Overall status:** Done
- **Last updated:** 2025-11-04
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Build reusable scenario infrastructure (adapters, fixtures, documentation) inside the `samples` module
  so future tasks can implement single-run comparisons (ICLI vs Commons Exec, zt-exec, NuProcess, JLine) in both Java
  and Kotlin.
- **Definition of Done:**
  - Shared types (`CommandInvocation`, `SingleRunExecutor`, `ScenarioExecutionResult`) exist with package-level
  annotations and Kotlin helpers.
  - Adapters for iCLI, Commons Exec, zt-exec, NuProcess, and JLine can execute a supplied command definition and return
  comparable results.
  - Fake-process + real-tool invocation builders (rooted in `java -version`) plus documentation (AGENTS/README updates)
  are available for upcoming scenarios.
  - Kotlin tests demonstrate harness usage by running both fake and real commands through every adapter.
- **Constraints:**
  - Follow the `com.github.ulviar.icli.samples.<scenario>.<approach>` layout from ICLI-036 and declare
  `@NotNullByDefault` per package.
  - Samples module remains documentation-only (no publishing) and must rely on the existing toolchain (Java 25,
  Kotlin/JUnit 6, Spotless, SpotBugs) via MCP Gradle tasks.
  - Fake process tooling must be cross-platform and not depend on shell-specific features.
- **Roles to notify:** Maintainer upon completion (feeds ICLI-039 scenario implementation).

## Planning
- **Scope summary:** Implement the single-run scenario harness (Java + Kotlin APIs), ship adapters for each alternative
  library, add fake/real command builders, and update module documentation to describe contribution workflow.
- **Proposed deliverables:**
  1. Core scenario types (`CommandInvocation`, `SingleRunExecutor`, `ScenarioExecutionResult`, Kotlin helpers) under
  `com.github.ulviar.icli.samples.scenarios.single`.
  2. Java adapters for iCLI, Commons Exec, zt-exec, NuProcess, and JLine plus Kotlin convenience factory.
  3. Fake process fixture (`FakeSingleRunProcess`) and helper builders (fake + `java -version`).
  4. Updated docs (samples README/AGENTS) detailing harness usage, package layout, and test expectations.
  5. Kotlin tests that exercise all adapters against fake + real commands.
- **Open questions / risks:**
  - Ensure NuProcess handler implementation remains deterministic across OSes.
  - JLine's `ExecHelper` merges stdout/stderr; document limitation so comparisons stay fair.
  - Validate that fake process execution via `java -cp … FakeSingleRunProcess` works in CI and respects timeouts.
- **Backlog link:** [context/tasks/backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-04.md](analysis/2025-11-04.md)
- **Knowledge consulted:** ICLI-036 plan, samples AGENTS/README, samples build, and core iCLI types (see log for
  details).
- **Readiness decision:** Implementation can begin; outstanding risks documented (NuProcess/JLine parity).

## Research
- **Requests filed:** None.
- **External outputs:** —
- **Summary:** —
- **Human response:** —

## Execution
- **History entries:** [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** Added shared scenario types, adapters for iCLI/Commons Exec/zt-exec/NuProcess/JLine,
  fake and real command builders, Kotlin helpers/tests, and documentation updates inside the `samples` module.
- **Testing:**
  - `execute_gradle_task` — `spotlessApply`
  - `run_gradle_tests` — `:samples:test`
  - `run_gradle_tests` — `test`
- **Follow-up work:** Subsequent tasks (ICLI-039, etc.) should reuse the harness and document scenario-specific tools.
- **Retrospective:** Harness complete; future work can focus on scenario implementations without redoing plumbing.

## Completion & archive
- **Archive status:** Archived 2025-11-04
- **Archive location:** context/tasks/archive/ICLI-038/README.md
- **Final verification:** Spotless + `:samples:test` + `test` executed after the harness landed.

## Decisions & notes
- **Key decisions:**
  - Fake process fixtures run via the module's `FakeSingleRunProcess` (bootstrapped with the current Java executable) to
  keep tests cross-platform.
  - The JLine adapter intentionally relies on `ExecHelper` and documents that stdout/stderr are merged, reflecting the
  limitations of that helper.
- **Risks:** Future scenarios relying on environment overrides/working-directory tweaks cannot use the JLine adapter for
  feature parity; document such gaps per scenario.
- **Links:** Related work — ICLI-036 plan (planning inputs), ICLI-039 (first scenario implementation).
