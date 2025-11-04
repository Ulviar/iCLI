# ICLI-029 — Align runners and docs with scenario focus

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-11-04
- **Owner:** Assistant

## Overview
- **Objective:** Document how the Essential API runners (standard and pooled) map to the refreshed execution scenarios
  so consumers can pick the right helper without spelunking through source.
- **Definition of Done:**
  - Runner coverage per scenario is described in README quick-start examples and the execution architecture brief.
  - Code samples show how to reach pooled runners via `PooledCommandService`, including guidance for scenario-specific
  helpers planned for listen-only/MCP/stateful conversations.
  - Backlog entry references this dossier and reflects the active stage.
  - Repository documentation uses consistent terminology for standard vs pooled runners and calls out pending scenario
  helpers.
- **Constraints:** Follow repository documentation and coding standards; keep `.commit-message` updated; run required
  formatting/tests before completion.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Update public documentation (README, architecture brief, related samples) to emphasise
  scenario-driven runner usage and describe the new pooled facade.
- **Proposed deliverables:** Refreshed README quick-start + scenario sections, updated execution architecture brief
  excerpts, updated backlog metadata, analysis/execution logs, `.commit-message`.
- **Open questions / risks:** Determine whether additional docs (roadmap, KB) need scenario callouts; ensure README
  examples remain buildable; confirm no code changes are needed for runner exports.
- **Backlog link:** [context/tasks/backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-11-04.md](analysis/2025-11-04.md)
- **Knowledge consulted:** See analysis log for the governance + roadmap docs that informed the plan (scenario
  catalogue, architecture brief, README audit, etc.).
- **Readiness decision:** Ready for execution; scope limited to documentation + backlog updates unless gaps appear.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Not applicable.
- **Human response:** Not applicable.

## Execution
- **History entries:**
  - [execution-history/2025-11-04.md](execution-history/2025-11-04.md)
- **Implementation highlights:** README now includes the expect-style sample plus a scenario cheat sheet, the execution
  architecture brief spells out scenario coverage for both standard and pooled facades, and backlog metadata reflects
  the active dossier.
- **Testing:** `python scripts/pre_response_checks.py`
- **Follow-up work:** Monitor ICLI-023/ICLI-024/ICLI-025/TBD-001 for the promised listen-only, MCP, and preset helpers
  so docs can be refreshed when the implementations land.
- **Retrospective:** Included in [execution-history/2025-11-04.md](execution-history/2025-11-04.md).

## Completion & archive
- **Archive status:** Archived 2025-11-04
- **Archive location:** context/tasks/archive/ICLI-029
- **Final verification:** `python scripts/pre_response_checks.py` (session completion checklist); documentation-only
  diff so no Gradle tasks were required.

## Decisions & notes
- **Key decisions:** _TBD_
- **Risks:** _TBD_
- **Links:** _TBD_
