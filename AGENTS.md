# Repository Agent Guidelines

## Scope

These guidelines apply to the entire repository unless a more specific `AGENTS.md` is created within a subdirectory.

## Tooling and Environment

- Primary production language: **Java 25**.
- Test language and framework: **Kotlin 2.2.20** with **JUnit 6**.
- Build system: **Gradle 9.1.0** with Kotlin DSL (`build.gradle.kts`).
- Minimum JDK and target JVM version: **25 (LTS)**.

## Code Style

- Java sources must follow **Palantir Java Format** and be enforced via **Spotless**.
- Kotlin sources must follow the official **Kotlin coding conventions** and be enforced via **Spotless**.
- Review `context/guidelines/icli/assistant-notes.md` for assistant-specific implementation preferences (e.g., when to
  use Java records).
- Answer detailed design questions in `EXPLANATION.md` (Russian) when requested; capture reasoning, alternatives, and
  test strategy there instead of the console when longer narratives are helpful.

## Quality Gates

- Configure and run **Spotless** for formatting and **SpotBugs** for static analysis as part of the build.
- All automated tests should be written in Kotlin using JUnit 6.

## Workflow Expectations

1. Prefer Gradle tasks for building, testing, formatting, and linting once the Gradle project is initialized (e.g.,
   `./gradlew build`, `./gradlew spotlessApply`, `./gradlew spotbugsMain`).
2. Keep README and other documentation up to date when behavior or tooling changes.
3. When adding or updating build tooling or library dependencies, prefer the latest stable versions available (excluding
   the pinned versions of Java, Kotlin, and Gradle) by checking for updates before committing changes.
4. Maintain a single proposed commit message in the repository root `.commit-message` file, updating it before ending a
   work session so it reflects changes since the last commit (the file is ignored by Git).
5. Before completing any task, explicitly review and refresh the `.commit-message` file to ensure it captures only the
   current work.

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

- Before starting any task, review the latest materials under `context/knowledge-base` to stay aligned with project
  context and decisions.
- Read `context/roadmap/project-roadmap.md` to stay connected to the overall delivery plan and current phase priorities.
- Maintain awareness of research outputs stored under `context/research`:
    - Consult `context/research/registry.md` before beginning work to understand which analyses are available and where
      to find details.
    - Use the registry to identify targeted documents that apply to the task instead of re-reading the entire directory,
      but revisit individual reports when their context is relevant.
- Follow the Markdown formatting rules in
  `context/guidelines/general/markdown-formatting.md` and review the file before editing Markdown documents.

## Repository Map

- Primary coordination instructions are kept in this file located at the repository root (`AGENTS.md`).
- When creating new modules or directories, add additional `AGENTS.md` files if extra guidance is required.
