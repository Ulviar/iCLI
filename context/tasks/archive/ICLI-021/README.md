# ICLI-021 — Audit documentation links & formatting

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-02
- **Owner:** Assistant

## Overview
- **Objective:** Restore valid relative links across Markdown documentation (especially archived dossiers) and enforce
  repository formatting standards.
- **Definition of Done:**
  - Identify and correct broken relative links caused by dossier archival or directory moves.
  - Run Markdown formatting sweep to ensure consistent style after edits.
  - Document remediation steps and update backlog status accordingly.
- **Constraints:** Apply changes via automation where practical; avoid altering intentional references; keep
  `.commit-message` current.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Normalise Markdown links within `context/` (with focus on archived task dossiers) and reformat
  files afterwards.
- **Proposed deliverables:** Updated Markdown files with corrected links, analysis/execution logs, backlog status
  change.
- **Open questions / risks:** Ensure automation does not rewrite legitimate cross-task links; consider future tooling to
  prevent regressions.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-02.md](analysis/2025-11-02.md)
- **Knowledge consulted:** See analysis log for references.
- **Readiness decision:** Ready; automated pass plus formatting will address the identified gap.

## Research
- **Requests filed:** Link to logs under `research/requests/` (delete if none). Assistants hand off research to humans.
- **External outputs:** Reference files stored in `context/research/`.
- **Summary:** Highlight conclusions or data points that influenced the plan.
- **Human response:** Capture who responded, the decision, and any follow-up guidance.

## Execution
- **History entries:**
  - [execution-history/2025-11-02.md](execution-history/2025-11-02.md)
- **Implementation highlights:** Added `scripts/normalize_markdown_links.py` and used it to repair archived dossier
  links; repository Markdown formatter run afterwards.
- **Testing:** Documentation-only; relied on `python scripts/format_markdown.py` and `scripts/pre_response_checks.py`
  (before final response).
- **Follow-up work:** Consider integrating link validation into archival checklist.
- **Retrospective:** Captured in [execution-history/2025-11-02.md](execution-history/2025-11-02.md).

## Completion & archive
- **Archive status:** Archived.
- **Archive location:** `context/tasks/archive/ICLI-021/`.
- **Final verification:** Link normaliser + markdown formatter executed; Gradle spotless/test rerun.

## Decisions & notes
- **Key decisions:** Adopted automated link normalisation script for archive clean-up; formatting enforced via existing
  repo tooling.
- **Risks:** Future dossier moves could reintroduce stale links if automation not integrated into archival workflow.
- **Links:** [Execution log](execution-history/2025-11-02.md)
