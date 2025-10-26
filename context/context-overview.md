# Context Overview Checklist

This checklist distils the minimum set of documents every assistant must re-read (or at least skim for changes) before
starting any new task. Follow it in order; if a document references further artefacts, open those immediately. Expect to
spend a few minutes here—skipping steps has already caused missed instructions.

## 1. Governance & Workflow
1. **AGENTS.md (root)** — confirms global constraints (tooling, formatting, documentation language, Gradle usage).
2. **context/tasks/README.md** — reiterates the task lifecycle, logging rules, and archive policy; ensures dossiers are
   structured correctly from planning through retrospective.

## 2. Code & Documentation Standards
3. **context/guidelines/coding/standards.md** — mandatory coding rules (nullability, records, TDD, docs).
4. **context/guidelines/icli/assistant-notes.md** — project-specific expectations (when to use records, TDD ritual,
   documentation-first tests).
5. **context/guidelines/general/markdown-formatting.md** — wrapping rules and formatting script requirements for any
   Markdown edits.

## 3. Product Direction
6. **context/roadmap/project-roadmap.md** — confirms which phase is active and the rationale behind current tasks.
7. **context/roadmap/execution-architecture-brief.md** — authoritative reference for API layers, runtime components,
   and pending design gaps.
8. **context/roadmap/execution-requirements.md** and **context/roadmap/execution-use-case-catalogue.md** — spell out
   the behavioural requirements and concrete scenarios that drive acceptance criteria.

## 4. Research & Knowledge Base
9. **context/research/registry.md** — index of available studies (e.g., PTY comparison, Kotlin audit). Open each linked
   document that is relevant to the current task’s scope.
10. **context/knowledge-base/operations/Java Terminal & Process Integration.md** — canonical operational guidance for
    launching processes, IO pumping, PTY decisions, and cross-platform nuances.

## 5. Task-Specific Inputs
11. **context/tasks/backlog.md** — identifies the active task, its Definition of Done, and dossier link.
12. **Task dossier (`context/tasks/<ID>/README.md` + logs)** — read the planning, analysis, and execution-history files
    before editing code. If the dossier is missing, create it immediately using the templates.

## Usage Notes
- Run through this checklist at the start of every work session. When new documents appear, append them to the relevant
  section so future assistants inherit the same baseline.
- If you discover instructions that conflict across documents, pause and clarify before writing code; log the outcome
  in the task dossier and update this overview if needed.
- Treat the checklist as blocking: do not begin implementation until each item has been reviewed for relevance to the
  current task.
- Before ending the session, execute the steps in `context/checklists/session-completion.md` (formatting/tests and
  `.commit-message` refresh) and record the outcome in the task dossier.
