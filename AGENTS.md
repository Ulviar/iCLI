# Repository Agent Guidelines

## Scope

These guidelines apply to the entire repository unless a more specific [AGENTS.md](/AGENTS.md) is created within a
subdirectory.

## Tooling and Environment

- Primary production language: **Java 25**.
- Test language and framework: **Kotlin 2.2.20** with **JUnit 6**.
- Build system: **Gradle 9.1.0** with Kotlin DSL (`build.gradle.kts`).
- Minimum JDK and target JVM version: **25 (LTS)**.
- Invoke Gradle exclusively through the MCP tools (`get_gradle_project_info`, `execute_gradle_task`,
  `run_gradle_tests`); do not call `./gradlew` directly from the shell.

## Code Style

- Java sources must follow **Palantir Java Format** and be enforced via **Spotless**.
- Kotlin sources must follow the official **Kotlin coding conventions** and be enforced via **Spotless**.
- Review [context/guidelines/icli/assistant-notes.md](/context/guidelines/icli/assistant-notes.md) for
  assistant-specific implementation preferences (e.g., when to use Java records).
- Read [context/guidelines/coding/standards.md](/context/guidelines/coding/standards.md) before making code changes; its
  rules are mandatory for all contributions.
- Use JetBrains nullability annotations: annotate packages with `@NotNullByDefault`, mark exceptional cases with
  `@Nullable`, and avoid other annotations until explicitly approved.
- Answer detailed design questions in [EXPLANATION.md](/EXPLANATION.md) (Russian) when requested; capture reasoning,
  alternatives, and test strategy there instead of the console when longer narratives are helpful.

## Quality Gates

- Configure and run **Spotless** for formatting and **SpotBugs** for static analysis as part of the build.
- All automated tests should be written in Kotlin using JUnit 6.

## Workflow Expectations

1. Prefer Gradle tasks for building, testing, formatting, and linting by calling the MCP tools (e.g., use
   `execute_gradle_task` with `["build"]` or `["spotlessApply"]`, `run_gradle_tests` for suites); skip direct shell
   invocations of `./gradlew`.
2. Treat `python scripts/list_changed_files.py` as the canonical definition of “changes since the last commit”. Use it
   to detect added or modified files instead of ad-hoc `git` commands.
3. **Before delivering any response while the working tree has changes, run `scripts/pre_response_checks.py`.** This
   script performs the required inspections (status, change list, markdown formatting) and reminds you to trigger MCP
   Gradle tasks; do not skip or replace it with manual steps.
4. Keep README and other documentation up to date when behavior or tooling changes.
5. When adding or updating build tooling or library dependencies, prefer the latest stable versions available (excluding
   the pinned versions of Java, Kotlin, and Gradle) by checking for updates before committing changes.
6. Maintain a single proposed commit message in the repository root `.commit-message` file, overwriting the file
   whenever the diff changes so it contains only one meaningful commit message for the current repository state (the
   file is ignored by Git).
7. Before completing any task, explicitly review the refreshed `.commit-message` to confirm no stale bullet points or
   legacy summaries remain.
8. Prior to ending a session, follow
   [context/checklists/session-completion.md](/context/checklists/session-completion.md) (formatting/tests +
   `.commit-message` refresh) and log the checklist completion inside the active task dossier.
9. When the maintainer requests manual validation, consult
   [context/guidelines/manual-checks/assistant-validation-steps.md](/context/guidelines/manual-checks/assistant-validation-steps.md)
   and execute the named sequence one step at a time, reporting after each step before continuing.

## Development Practices

- Apply **test-driven development (TDD)**: write or update failing tests before implementing behaviour, keep the suite
  passing, and ensure every proposed change is backed by automated coverage.
- Never leave the repository in a broken state—run the relevant Gradle compile/test tasks before sharing code to confirm
  it builds.
- Use `TODO` markers where helpful during iterative design; include owner/context in the comment and raise backlog
  follow-ups when the remaining work cannot be resolved immediately.

## Documentation

All project documentation, comments, and commit messages must be written in **English**.

## Knowledge Base

- Before starting any task, read [context/context-overview.md](/context/context-overview.md) end-to-end; it lists the
  mandatory documents to revisit each session and links to their latest locations.
- After completing the overview checklist, review the latest materials under `context/knowledge-base` to stay aligned
  with project context and decisions.
- Read [context/roadmap/project-roadmap.md](/context/roadmap/project-roadmap.md) to stay connected to the overall
  delivery plan and current phase priorities.
- Maintain awareness of research outputs stored under `context/research`:
    - Consult [context/research/registry.md](/context/research/registry.md) before beginning work to understand which analyses are available and where
      to find details.
    - Use the registry to identify targeted documents that apply to the task instead of re-reading the entire directory,
      but revisit individual reports when their context is relevant.
- Follow the Markdown formatting rules in
  [context/guidelines/general/markdown-formatting.md](/context/guidelines/general/markdown-formatting.md) and review the
  file before editing Markdown documents.

## Repository Map

- Primary coordination instructions are kept in this file located at the repository root ([AGENTS.md](/AGENTS.md)).
- When creating new modules or directories, add additional [AGENTS.md](/AGENTS.md) files if extra guidance is required.
