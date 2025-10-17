# ICLI-001 — Establish Assistant-Managed Task Workflow

## Status
- **Lifecycle stage:** Execution
- **Overall status:** Active
- **Last updated:** 2025-10-17
- **Owner:** AI Assistant

## Overview
- **Objective:** Design and document a lightweight workflow that lets assistants plan, analyse, research, and execute
  repository tasks without an external tracker.
- **Definition of Done:**
  - `context/tasks/README.md` documents the lifecycle and links to templates.
  - Templates exist for task dossiers, analysis logs, research requests, and execution histories.
  - `context/tasks/backlog.md` reflects the workflow fields.
  - Example dossier with lifecycle placeholders is available.
  - `context/README.md`, `context/guidelines/general/contributor-guidelines.md`, and
    `context/workflow/collaboration.md` mention the workflow.
- **Constraints:** Maintain Markdown style rules, keep tooling references aligned with repo standards, and integrate the
  workflow into existing context docs.
- **Stakeholders:** Repository maintainers, assistants contributing to future tasks.

## Planning
- **Scope summary:** Introduce a repository-native workflow for backlog management, lifecycle logging, and templates.
- **Proposed deliverables:** Lifecycle README, Markdown templates, backlog refresh, cross-reference updates, and this
  dossier updated to demonstrate usage.
- **Open questions / risks:** Decide whether completed dossiers stay in place or move to an archive (defer to future
  policy); verify identifier format remains `ICLI-###`.
- **Backlog link:** [context/tasks/backlog.md](../backlog.md)

## Analysis
- **Log entries:** [`analysis/2025-10-17.md`](analysis/2025-10-17.md)
- **Knowledge consulted:**
  - [`context/roadmap/project-roadmap.md`](../../roadmap/project-roadmap.md) — Reframed the task as a strategic workflow enabler rather than a narrow documentation tweak; easy to find via the context index.
  - [`context/guidelines/general/markdown-formatting.md`](../../guidelines/general/markdown-formatting.md) — Dictated template structure and wrapping rules; immediately accessible from the guidelines overview.
  - [`context/tasks/backlog.md`](../backlog.md) — Highlighted schema gaps that required new status, stage, and dossier fields; required manual inspection of the existing table but no navigation issues.
- **Readiness decision:** Execution approved — requirements and deliverables are clear.

## Research
- **Requests filed:** None to date.
- **External outputs:** Not applicable.
- **Summary:** Existing repository knowledge was sufficient; no additional research required.
- **Human response:** Not applicable — no hand-off initiated.

## Execution
- **History entries:** [`execution-history/2025-10-17.md`](execution-history/2025-10-17.md)
- **Implementation highlights:** Pending — to be filled once documentation and templates are updated.
- **Testing:** Documentation-only task; verify Markdown formatting manually.
- **Follow-up work:** Document any policy decisions on archiving dossiers once agreed.

## Completion & archive
- **Archive status:** Active (not archived).
- **Archive location:** Pending — remains under `context/tasks/ICLI-001/`.
- **Final verification:** To be captured when Definition of Done is satisfied.

## Decisions & notes
- **Key decisions:** Adopted lifecycle stages (`Planning`, `Analysis`, `Research`, `Execution`, `Review`, `Done`) and
  per-stage log files with dated naming.
- **Risks:** Need to reinforce that logs require continuous updates to remain useful.
- **Links:** [context/tasks/README.md](../README.md), [context/tasks/templates/](../templates/)
