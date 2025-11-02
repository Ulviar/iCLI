# ICLI-019 — Align roadmap/backlog with refreshed scenarios

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-02
- **Owner:** Assistant

## Overview
- **Objective:** Update roadmap artefacts and the backlog so every milestone and queued task reflects the refreshed
  execution scenarios delivered under ICLI-018.
- **Definition of Done:**
  - Roadmap documents (phase sequencing, architecture briefs) explicitly reference the current scenario catalogue and
  highlight scenario coverage or gaps.
  - Backlog entries are regrouped or adjusted so upcoming implementation tasks map cleanly onto the scenario taxonomy.
  - Task dossier and supporting logs document decisions, verification steps, and follow-up actions.
  - `.commit-message` lists a single up-to-date commit summary for the pending diff.
- **Constraints:** Follow repository documentation rules (Markdown formatting, English language), keep backlog edits in
  sync with dossier status updates, and coordinate changes with the requirements captured in
  [execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md).
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Align roadmap phases and backlog tasks with the revised execution scenario taxonomy, ensuring each
  document points back to the canonical catalogue and clarifies priority focus areas.
- **Proposed deliverables:** Updated roadmap/backlog entries, refreshed task dossier logs, and an updated
  `.commit-message`.
- **Open questions / risks:** Determine whether additional roadmap documents (e.g., pooling architecture brief) require
  immediate updates; confirm that no downstream tasks become stale after the realignment.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-02.md](analysis/2025-11-02.md)
- **Knowledge consulted:** See dated analysis logs for the specific documents that informed the plan.
- **Readiness decision:** Ready to proceed with roadmap/backlog updates based on the analysis above.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:**
  - [execution-history/2025-11-02.md](execution-history/2025-11-02.md)
- **Implementation highlights:** Roadmap documents reference the refreshed scenario catalogue; backlog rows updated with
  status/stage adjustments and scenario notes; dossier captured analysis/execution details.
- **Testing:** `python scripts/list_changed_files.py`; `python scripts/pre_response_checks.py`.
- **Follow-up work:** Resume ICLI-016 once roadmap/API boundary tasks close; confirm if additional roadmap briefs need
  scenario references.
- **Retrospective:** Captured in [execution-history/2025-11-02.md](execution-history/2025-11-02.md).

## Completion & archive
- **Archive status:** Archived 2025-11-02.
- **Archive location:** [context/tasks/archive/ICLI-019/](/context/tasks/archive/ICLI-019)
- **Final verification:** Roadmap/backlog checked via `scripts/pre_response_checks.py`; `.commit-message` refreshed.

## Decisions & notes
- **Key decisions:**
  - Roadmap and supporting architecture briefs now cite the scenario catalogue explicitly.
  - Backlog expanded with new tasks (ICLI-023/024/025) to cover listen-only, MCP, and pooled conversation scenarios.
- **Risks:** Monitor whether additional roadmap documents should inherit the same scenario alignment pattern.
- **Links:** [Execution use case catalogue](/context/roadmap/execution-use-case-catalogue.md)
