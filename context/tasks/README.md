# Assistant-Managed Task Workflow

This directory hosts the backlog, task dossiers, and templates that enable AI assistants to execute work without an
external tracker. Every task flows through the same lightweight lifecycle so history, decisions, and outputs stay
discoverable inside the repository.

## Workflow lifecycle

1. **Planning** — claim a backlog entry, create a dossier from the template, restate the objective, and capture the
   Definition of Done plus scope notes. Planning finishes when the task has clear acceptance criteria and open
   questions are documented.
2. **Analysis** — consult the knowledge base, roadmap, research registry, and relevant source files. Record findings in
   dated analysis logs, call out gaps, and note whether additional research is required. Each knowledge entry must link
   (via relative Markdown paths) to the exact document or file that materially changed your understanding. Capture how
   that source shifted the plan compared with the initial task description.
3. **Research** *(optional)* — when missing information blocks progress, pause execution. Prepare a research request log
   and hand it to a human collaborator. The assistant must not run external prompts; wait for the human to respond
   before resuming work.
4. **Execution** — implement the change, document tests and verification steps, and log follow-up actions. Execution
   history entries should call out code changes, documentation updates, and any deviations from the plan.
5. **Retrospective** — confirm deliverables against the task goal and Definition of Done, then capture assistant-facing
   process improvement suggestions before closing the work. Record these suggestions in the final execution history
   entry so future tasks benefit from the feedback loop.
   - During execution, apply test-driven development (TDD): add/adjust tests first, watch them fail, implement code to
     pass, then rerun the suite before moving on. Never leave a task with a red build.

Assistants should update the dossier status field as the work advances (e.g., `Planning`, `Analysis`, `Research`,
`Execution`, `Retrospective`, `Review`, `Done`) and note blockers explicitly. Each stage expects a corresponding log
entry before moving forward.

Role expectations for the maintainer, assistants, and future consumers are defined in
[`context/guidelines/icli/project-roles.md`](../guidelines/icli/project-roles.md); reference that document whenever a
template requests reviewers or audience notes.

## Standard dossier structure

Task dossiers live under `context/tasks/<TASK-ID>/` and own the working notes for a single backlog item. Copy the
`templates/task-dossier.md` file into `context/tasks/<TASK-ID>/README.md`, then maintain the following layout:

```
context/tasks/<TASK-ID>/
├── README.md                  # Primary dossier (copied from the template)
├── analysis/                  # Dated analysis logs
│   └── YYYY-MM-DD.md
├── research/
│   └── requests/              # Research request logs
│       └── YYYY-MM-DD.md
└── execution-history/         # Execution logs
    └── YYYY-MM-DD.md
```

Create each subdirectory only when you have actual files for it. Leave the dossier with just `README.md` until logs or
requests exist, and do not add placeholder files (e.g., `.gitkeep`) solely to populate empty directories.

- **Analysis logs** capture knowledge consulted (with Markdown links), insights gained, and readiness to proceed. Only
  reference sources that significantly altered your approach versus the initial brief, and record how each source helped
  or hindered the task. Fill them using
  `templates/analysis-log.md`.
- **Research request logs** describe the problem, required information, working hypotheses, prompt, and expected
  human hand-off details. Create them from `templates/research-request.md` when requesting human assistance.
- **Execution history logs** chronicle implementation steps, validation commands, test runs, and documentation updates.
  Use `templates/execution-history.md`.

Archived or superseded dossiers can be moved to an `archive/` directory if project policy evolves, but keep references
stable so historical links remain valid.

## Backlog conventions

`context/tasks/backlog.md` tracks the queue of work. Maintain the following columns:

| Column        | Purpose                                                                             |
|---------------|-------------------------------------------------------------------------------------|
| `ID`          | Canonical task identifier (e.g., `ICLI-001`).                                       |
| `Title`       | Short headline that describes the change.                                           |
| `Status`      | High-level state (`Backlog`, `Active`, `Blocked`, `Done`).                          |
| `Stage`       | Current workflow stage (`Planning`, `Analysis`, `Research`, `Execution`, `Review`, `Done`). |
| `Goal`        | Desired outcome or success condition.                                               |
| `Constraints` | Non-negotiable rules, dependencies, or tooling requirements.                        |
| `Dossier`     | Relative path to the task dossier `README.md`.                                      |
| `Notes`       | Optional free-form context or links to related work.                                |

When starting a task, update the backlog row with the dossier link and set the status to `Active`. Once the Definition
of Done matches the deliverables, flip the status to `Done` and ensure the final execution log includes verification
results.

## Templates

Templates for all logs live under `context/tasks/templates/`:

- `task-dossier.md`
- `analysis-log.md`
- `research-request.md`
- `execution-history.md`

Copy the template into the corresponding dossier folder, then replace placeholder text with task-specific details. The
templates use sentence case headings and 120-character wrapping to comply with the repository Markdown guidelines.

## Research hand-off protocol

When analysis uncovers a knowledge gap that blocks progress:

1. Document the gap in the latest analysis log with links to the consulted material.
2. Draft a research request using `templates/research-request.md`, capture the outstanding questions, and note the
   proposed prompt or manual steps for the human collaborator. Save the file as
   `context/tasks/<TASK-ID>/research/requests/research-request.md` (add a date suffix if multiple hand-offs occur).
3. Pause task execution and notify the human owner with the newly created request file path.
4. Await the human response:
   - If the human provides results, store any detailed findings under `context/research/` (or the location they specify)
     and update the research request log with the outcome.
   - If the human declines or defers the research, record the decision in the request log and adjust the task plan.
5. Resume task execution only after the human confirms the outcome.

Assistants must not run external prompts or conduct out-of-band research themselves; all investigations flow through the
human-in-the-loop hand-off.

## Feedback loops for context quality

Consistent, link-backed logging enables the team to learn which documents drive successful outcomes and where context is
missing or hard to discover. When updating analysis or execution logs:

- Note which sources were most impactful and why.
- Mention any difficulties in locating information so future improvements can target navigation pain points.
- Flag missing or outdated context in the dossier so maintainers can schedule follow-up research or documentation work.

These feedback loops power tooling that can analyse link usage and highlight opportunities to enrich the shared
knowledge base.

## Archive strategy

Active work lives directly under `context/tasks/`. After a task meets its Definition of Done:

1. Ensure the final execution log summarises verification and follow-up actions.
2. Update `context/tasks/backlog.md` to set the task `Status` to `Done`, change the `Stage` to `Review` (if applicable)
   and then `Done`, and point the `Dossier` column to `context/tasks/archive/<TASK-ID>/README.md`.
3. Move the entire dossier directory into `context/tasks/archive/<TASK-ID>/` while preserving the folder structure.
4. Update the dossier’s “Completion & archive” section with the move date and any hand-off notes.

Archived dossiers must remain immutable unless you are fixing broken links or clarifying historical context. The archive
index at [`context/tasks/archive/README.md`](archive/README.md) contains the detailed checklist.

## Activity logging expectations

- Create at least one dated analysis log before beginning execution; include Markdown links only for documents, notes,
  or files that added substantial new insight beyond the task description, and capture how each influenced the plan.
- Use research request logs to trigger human-in-the-loop support. Assistants document the request and pause work until a
  human responds with results or declines the investigation.
- Record every test or verification command in execution history files, including both successes and failures.
- Verify dependency/tool version updates by checking their official release feeds and capturing the consulted source in
  the execution log when a version bump occurs.
- Close the final execution history entry with a short retrospective: restate whether outcomes met the goal/DoD and list
  at least one assistant process improvement suggestion (even if it is “none this time”).
- Update the dossier `README.md` as the single source of truth: current stage, links to logs, Definition of Done
  updates, and outstanding risks.
- Maintain `.commit-message` at the repository root with a single proposed commit message reflecting all changes since
  the previous Git commit; refresh it before ending the session.

## Getting started checklist

1. Identify a backlog entry and copy `templates/task-dossier.md` into a new dossier directory.
2. Update the backlog row with the dossier link, status, and initial stage set to `Planning`.
3. Complete a planning entry in the dossier, then add an analysis log documenting the knowledge consulted.
4. Conduct research and execution steps as needed, recording each in their respective log files.
5. Close out the task by documenting tests, updating the backlog status to `Done`, and capturing follow-up actions in
   the execution history.
