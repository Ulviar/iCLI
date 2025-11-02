# ICLI-020 — Clarify public API surface

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-02
- **Owner:** Assistant

## Overview
- **Objective:** Define and enforce iCLI’s supported package boundaries so consumers can rely on documented Essential
  and Advanced APIs while internal runtime details remain encapsulated.
- **Definition of Done:**
  - Public vs internal package policy documented for the module, including references to Essential and Advanced APIs.
  - `module-info.java` exports only the intended public packages and reflects the documented policy.
  - Internal runtime classes are relocated or renamed as needed so exported packages expose only supported types.
  - Tests, SpotBugs, and formatting succeed after the boundary changes, and documentation mirrors the final layout.
- **Constraints:** Avoid breaking currently documented Essential/Advanced APIs; preserve scenario coverage described in
  the execution catalogue; follow repository formatting and documentation rules.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Inventory existing packages, categorise them as public or internal, tighten exports accordingly,
  move or rename runtime helpers that should remain internal, and document the supported surface for both consumers and
  maintainers.
- **Proposed deliverables:** Updated `module-info.java`; relocation or façade for `StandardProcessEngine` plus
  diagnostics types so only vetted APIs remain in exported packages; README “Supported packages” section for external
  consumers; maintainer notes under [context/guidelines/icli/public-api.md](../../guidelines/icli/public-api.md);
  dossier logs and refreshed `.commit-message`.
- **Open questions / risks:** Confirm no downstream consumers rely on internal package names in archived docs; monitor
  for binary compatibility issues caused by moving `StandardProcessEngine` once artifacts are published.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-02.md](analysis/2025-11-02.md)
- **Knowledge consulted:** See the analysis log for document references (architecture brief, execution-use-case
  catalogue, README).
- **Readiness decision:** Executing the restructuring plan.

## Research
- **Requests filed:** _None_
- **External outputs:** _None_
- **Summary:** _Pending_
- **Human response:** _Not applicable_

## Execution
- **History entries:**
  - [execution-history/2025-11-02.md](execution-history/2025-11-02.md)
- **Implementation highlights:** Renamed Advanced API packages to `com.github.ulviar.icli.engine.*`, tightened exports,
  refreshed documentation, and updated tests/logs to match.
- **Testing:** `./gradlew spotlessApply`, `./gradlew test`, `./gradlew integrationTest`, `python
  scripts/pre_response_checks.py`.
- **Follow-up work:** Monitor archived docs for references to removed `core.*` packages.
- **Retrospective:** Goal achieved; see execution history entry for details.

## Completion & archive
- **Archive status:** Archived 2025-11-02
- **Archive location:** context/tasks/archive/ICLI-020/
- **Final verification:** Formatting + unit/integration suites via Gradle; `scripts/pre_response_checks.py`.

## Decisions & notes
- **Key decisions:** Adopted `com.github.ulviar.icli.engine.*` namespace for Advanced API consumers, keeping internals
  under `.engine.runtime.internal`.
- **Risks:** Watch for stale package references in archived documents.
- **Links:** [context/guidelines/icli/public-api.md](../../guidelines/icli/public-api.md)
