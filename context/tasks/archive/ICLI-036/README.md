# ICLI-036 — Plan samples module scope & dossier

## Status
- **Lifecycle stage:** Planning (complete)
- **Overall status:** Done
- **Last updated:** 2025-11-04
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Capture the scope, success criteria, and planning artefacts required to launch the `samples` module
  that hosts ergonomic comparisons between iCLI and alternative libraries.
- **Definition of Done:**
  - Dossier documents objectives, DoD, constraints, scope summary, deliverables, and open questions for the samples
  module through the first single-run scenario.
  - Backlog entry ICLI-036 is linked to this dossier and reflects the final Done status.
  - Follow-on implementation tasks (ICLI-037…ICLI-039) reference this plan for module scaffolding, harness creation, and
  Scenario #1 execution.
- **Constraints:**
  - Samples must demonstrate both Java and Kotlin usage, emphasising code readability over benchmarks.
  - Module remains in-repo only (not published to Maven Central or CI release artifacts).
  - Scenarios require two tests (fake process + real CLI) with cross-platform-friendly tool choices unless intentionally
  platform-specific.
  - Repository-wide standards apply (Java 25, Kotlin 2.2.20/JUnit 6, Spotless/SpotBugs, AGENTS.md guidance).
- **Roles to notify:** Maintainer / project lead when planning is ready for review.

## Planning
- **Scope summary:** Define how the new `samples` module will be structured (multi-module Gradle layout, AGENTS rules,
  READMEs), outline the scenario taxonomy, and specify expectations for Java/Kotlin parity plus testing requirements
  ahead of any implementation work.
- **Proposed deliverables:**
  1. Documented module purpose, non-publishing stance, and alignment with roadmap goals.
  2. Scenario template describing package layout (`com.github.ulviar.icli.samples.<scenario>.<library>`), adapters, and
  test strategy (fake vs real CLI).
  3. Checklist of dependencies and helper utilities needed before Scenario #1 begins (e.g., Commons Exec, zt-exec,
  NuProcess, JLine 3, fake process fixtures, and a guard to ensure `java -version` is available).
- **Open questions / risks:**
  - Decide how to guard real-tool tests in CI when the tool is unavailable (JUnit tags vs assumptions vs opt-in profile)
  even though Scenario #1 will standardise on `java -version`.
  - Ensure the samples module stays isolated from publishing/CI release automation once the build becomes multi-module.
  - Clarify documentation expectations (root README vs module README) to avoid duplication.
- **Backlog link:** [backlog.md](../../backlog.md)

## Analysis
- **Log entries:** [analysis/2025-11-04.md](analysis/2025-11-04.md) — Captures the repository rules and roadmap context
  guiding the samples module plan plus remaining questions (CI guard strategy, documentation placement).
- **Knowledge consulted:** AGENTS.md, project conventions, roadmap, and use-case catalogue (see analysis log for notes).
- **Readiness decision:** Planning deliverables complete; execution tasks (ICLI-037…ICLI-039) can start using the
  captured plan and `java -version` scenario selection.

## Research
- **Requests filed:** None.
- **External outputs:** —
- **Summary:** —
- **Human response:** —

## Execution
- **History entries:** _Pending._
- **Implementation highlights:** —
- **Testing:** —
- **Follow-up work:** —
- **Retrospective:** —

## Completion & archive
- **Archive status:** Archived 2025-11-04
- **Archive location:** context/tasks/archive/ICLI-036/README.md
- **Final verification:** Planning artefacts reviewed; no code or tests were required.

## Decisions & notes
- **Key decisions:**
  - Samples module is documentation/ergonomics-only; no Maven Central publication planned.
  - Each scenario must feature iCLI alongside at least one alternative library for both Java and Kotlin call sites.
  - Scenario #1 will use `java -version` as the shared real CLI command, guaranteeing availability across supported
  platforms.
- **Risks:**
  - Real-tool tests may become flaky if chosen CLI is unavailable or behaves differently across platforms.
  - Introducing multi-module build requires careful coordination with existing scripts (`pre_response_checks.py`),
  otherwise automation may mis-detect changes.
- **Links:**
  - [backlog.md](../../backlog.md)
  - Related follow-on tasks: ICLI-037, ICLI-038, ICLI-039.
- **Next actions:**
  - Capture `java -version` assumptions plus guard strategy for the real-tool test case in Scenario #1 documentation.
  - Enumerate required third-party dependencies and Gradle wiring details for the upcoming `samples` module scaffold
  (feeds ICLI-037).
  - Draft the scenario harness abstractions and testing utilities outline before ICLI-038 begins implementation.
