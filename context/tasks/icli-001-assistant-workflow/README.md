# ICLI-001 — Establish Assistant-Managed Task Workflow

## Objective
Design and document a lightweight backlog workflow that AI assistants can use to plan, analyse, research, and execute
repository tasks without relying on an external tracker.

## Definition of Done
- `context/tasks/README.md` explains the workflow lifecycle (planning, analysis, research, execution) and links to the
  supporting templates.
- Templates exist for task dossiers (`README.md`), analysis logs, research request logs, and execution histories.
- The backlog format in `context/tasks/backlog.md` is updated (or confirmed) to match the new workflow fields.
- An example task dossier demonstrates the structure, including placeholders for each lifecycle stage.
- Relevant references in `context/README.md`, `context/guidelines/general/contributor-guidelines.md`, and
  `context/workflow/collaboration.md`
  mention how to engage with the assistant-managed workflow.

## Context
- The roadmap in `context/roadmap/project-roadmap.md` captures phase-level milestones but lacks actionable tasks.
- Previous guidance for assistants lived under the retired `ai/taskTracker`; the repository now centralizes knowledge in
  `context/`.
- Contributors need a repeatable, machine-friendly format for breaking down work while preserving history inside the
  repository.

## Desired Capabilities
1. **Planning stage** — selecting a backlog entry and producing a detailed scope with Definition of Done before coding.
2. **Analysis stage** — inspecting repository knowledge to determine readiness or identify missing information.
3. **Research stage** *(optional)* — capturing information requests with problem statements, required data, hypotheses,
   and a polished LLM prompt; storing results under `context/research/`.
4. **Execution stage** — implementing the change, recording tests/documentation updates, and noting follow-up work or
   knowledge base additions.

## Deliverables
- A README or guide at `context/tasks/README.md` describing how assistants should use the workflow.
- Markdown templates stored alongside the README (e.g., `analysis-template.md`, `research-request-template.md`,
  `history-template.md`).
- Updates to existing documents that need to reference the new workflow (project guidelines, collaboration notes, etc.).

## Open Questions
- How should task identifiers be structured (e.g., `ICLI-001`, slug-based, or both)?
- Where should completed task dossiers live once finished—stay in place or move to an archive?
- Should research outputs be summarised inside the task dossier or linked out to dedicated files under
  `context/research/`?

## Next Steps
1. Draft the lifecycle description and templates.
2. Validate that the backlog format maps cleanly to the lifecycle (fields for goal, constraints, links to dossiers).
3. Update cross-references in existing documentation.
4. Demonstrate the workflow with this task dossier once implementation begins.
