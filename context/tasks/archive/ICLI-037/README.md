# ICLI-037 — Introduce multi-module build & samples skeleton

## Status
- **Lifecycle stage:** Retrospective
- **Overall status:** Done
- **Last updated:** 2025-11-04
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Convert the project to a multi-module Gradle build and scaffold a new `samples` module that depends on
  the core library while remaining unpublished and documentation-focused.
- **Definition of Done:**
  - `settings.gradle.kts` recognises both `:icli` (root) and `:samples` modules; shared configuration is factored so
  Spotless/SpotBugs/toolchains apply consistently.
  - A new `samples` module exists with AGENTS.md, README, Java + Kotlin source roots, dependency wiring to the main
  library, and placeholders for upcoming scenarios.
  - The module is excluded from Maven Central/release publishing and CI release flows per repo policy.
  - Documentation (root README or appropriate context docs) references the new module and its purpose.
- **Constraints:**
  - Must honour repository standards (Java 25, Kotlin 2.2.20/JUnit 6, Spotless, SpotBugs) using Gradle MCP tools only.
  - Samples module stays internal (no publication); maintain workspace-write sandbox rules.
  - Follow guidance captured in [ICLI-036 plan](../ICLI-036/README.md) regarding scenario structure and dependency
  expectations.
- **Roles to notify:** Maintainer once skeleton is in place for subsequent scenario tasks.

## Planning
- **Scope summary:** Introduce multi-module Gradle config, set up shared plugin/toolchain logic, create the `samples`
  module directory tree with AGENTS.md/README/src placeholders, and ensure build/test tasks treat it correctly (e.g.,
  optional tests, dependency on core module, no publishing).
- **Proposed deliverables:**
  1. Updated Gradle settings + root build file reflecting the new module and shared conventions.
  2. [samples/AGENTS.md](../../../../samples/AGENTS.md), README, basic package structure
  (`com.github.ulviar.icli.samples`) and empty Java/Kotlin classes or placeholders ready for future scenarios.
  3. Build logic that keeps the samples module out of publication tasks while enabling Spotless/SpotBugs/test configs.
- **Open questions / risks:**
  - Ensure repository scripts (`pre_response_checks.py`, future formatting hooks) still operate after multi-module
  conversion.
  - Decide whether samples tests should run with regular `./gradlew test` or remain optional; coordinate
  tags/assumptions for future real-tool scenarios.
  - Validate that dependency declarations avoid circular references (samples should depend on main but not vice versa).
- **Backlog link:** [backlog.md](../../backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-04.md](analysis/2025-11-04.md) — Records the build review, dependency version
  research, and outstanding considerations for CI handling of samples tests.
- **Knowledge consulted:** See analysis log (ICLI-036 plan, build.gradle.kts, settings.gradle.kts, library release
  sources).
- **Readiness decision:** Ready to implement multi-module conversion and samples skeleton.

## Research
- **Requests filed:** None.
- **External outputs:** —
- **Summary:** —
- **Human response:** —

## Execution
- **History entries:** [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** Added the `samples` Gradle module (build file, AGENTS/README, placeholder Java/Kotlin
  sources, smoke test), updated settings/root README, and wired competitor dependencies scoped to samples only.
- **Testing:**
  - `execute_gradle_task` — `spotlessApply`
  - `run_gradle_tests` — `test`
- **Follow-up work:** ICLI-038/ICLI-039 will introduce the scenario harness plus the first single-run sample using `java
  -version` per the plan.
- **Retrospective:** Converted the build to multi-module, added the samples skeleton, documented the workflow, and
  verified formatting/tests; ready for follow-on scenario tasks.

## Completion & archive
- **Archive status:** Archived 2025-11-04
- **Archive location:** context/tasks/archive/ICLI-037/README.md
- **Final verification:** Spotless + full `test` suite executed after multi-module conversion.

## Decisions & notes
- **Key decisions:**
  - Samples module must not publish artifacts; it is documentation/demo only.
  - Shared build logic should live centrally (root `build.gradle.kts` or convention plugin) to avoid duplication.
- **Risks:**
  - Multi-module refactor could disrupt existing IDE/import behaviour if not coordinated.
  - Scripts referencing hardcoded paths may require updates once `samples` exists.
- **Links:**
  - [ICLI-036 plan](../ICLI-036/README.md)
  - Follow-on tasks: ICLI-038 (scenario harness) and ICLI-039 (Scenario #1).
