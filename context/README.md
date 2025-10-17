# Context Library

This directory aggregates project knowledge that both humans and AI assistants can reuse. Each subdirectory focuses on a
specific theme and files are written in English.

- `guidelines/` – reusable standards and iCLI-specific conventions.
- `knowledge-base/` – long-lived background material, past experiments, and archival notes.
- `research/` – targeted investigations and the registry that indexes them.
- `roadmap/` – phase-level plans and historical planning documents.
- `workflow/` – collaboration, release, and operational playbooks.
- `testing/` – detailed guidance for test strategy, fixtures, and expectations.
- `tasks/` – backlog, active task dossiers, and templates for the assistant-managed workflow (start with
  [`context/tasks/README.md`](tasks/README.md)).

See [`context/guidelines/icli/project-roles.md`](guidelines/icli/project-roles.md) for the single-maintainer role model
and target audience definitions used throughout the documentation.

When adding new material, extend the most relevant section and link back to it from higher-level guides so future work
can discover it quickly.
