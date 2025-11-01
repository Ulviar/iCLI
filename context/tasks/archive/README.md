# Archived Task Dossiers

Completed task dossiers move here once their Definition of Done is satisfied and the backlog row is updated to `Done`.
Each archived task retains its original structure (dossier, logs, research requests) so historical context remains
intact.

## Contents

- [`ICLI-001`](ICLI-001/README.md) — Assistant-managed workflow documentation and templates (archived 2025-10-17).
- [`ICLI-002`](ICLI-002/README.md) — Execution requirements brief covering single-run, interactive, and pooled process
  modes (archived 2025-10-18).
- [`ICLI-003`](ICLI-003/README.md) — Execution use case catalogue and constraint summary for roadmap Phase 2 (archived
  2025-10-18).
- [`ICLI-004`](ICLI-004/README.md) — Process execution architecture outline aligning API surfaces with roadmap guidance
  (archived 2025-10-24).
- [`ICLI-006`](ICLI-006/README.md) — PTY dependency evaluation selecting pty4j and documenting optional JLine usage for
  prompt experiments (archived 2025-10-25).
- [`ICLI-007`](ICLI-007/README.md) — Blocking vs non-blocking client API strategy decision record (archived 2025-10-26).
- [`ICLI-008`](ICLI-008/README.md) — Single command executor implementation with bounded capture and timeout handling
  (archived 2025-10-26).
- [`ICLI-009`](ICLI-009/README.md) — PTY-backed interactive session runtime with idle supervision and tests (archived
  2025-10-28).
- [`ICLI-010`](ICLI-010/README.md) — Streaming output capture plus diagnostics listener wiring (archived 2025-10-28).
- [`ICLI-012`](ICLI-012/README.md) — Client async scheduler and helper APIs (archived 2025-10-26).
- [`ICLI-015`](ICLI-015/README.md) — ProcessPool runtime implementation with diagnostics, retirement policies, and
  end-to-end coverage (archived 2025-11-01).

## Archival checklist

1. Confirm the final execution history entry documents verification steps and follow-up actions.
2. Update `context/tasks/backlog.md`:
   - Set the task `Status` to `Done`.
   - Point the `Dossier` column to the archived location (`context/tasks/archive/<TASK-ID>/README.md`).
3. Move the entire task directory from `context/tasks/<TASK-ID>/` to `context/tasks/archive/<TASK-ID>/`.
4. Add an archive note in the dossier’s “Completion & archive” section indicating the move date.

Do not modify archived dossiers except to correct broken links or add retrospective notes that clarify history.
