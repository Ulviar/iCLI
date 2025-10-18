# Contributor Guidelines Overview

This file captures reusable expectations for Java/Kotlin libraries that use Gradle. Project-specific conventions for
iCLI live under [`context/guidelines/icli/`](../icli/).

## Orientation

- Review the repo root [`AGENTS.md`](../../../AGENTS.md) before starting work and add scoped agents when new modules
  require additional rules.
- Use [`context/README.md`](../../README.md) as the entry point for domain knowledge, research history, and workflow
  playbooks. Follow the assistant-managed process in [`context/tasks/README.md`](../../tasks/README.md) when executing
  backlog work.
- Familiarise yourself with the single-maintainer role model described in
  [`context/guidelines/icli/project-roles.md`](../icli/project-roles.md) so responsibilities and approvals are clear.
- Follow the Markdown formatting standards in
  [`context/guidelines/general/markdown-formatting.md`](markdown-formatting.md).

## Source layout & ownership

- Place production Java sources in `src/main/java` and supporting resources in `src/main/resources`.
- Write tests in Kotlin under `src/test/kotlin`, keeping fixtures near the tests that own them.
- Store long-form documentation in a dedicated knowledge directory (for example, `context/`) so future contributors can
  locate design history and supporting references.

## Code standards

- Target Java 25 for production code and apply Google Java Format using Spotless (`./gradlew spotlessApply`).
- Format Kotlin sources with the official style via Spotless and keep tests idiomatic Kotlin.
- Prefer immutable data structures, explicit null handling (`Optional` or Kotlin nullable types), and documented
  concurrency decisions.

## Build & automation

- Run all tasks through the Gradle wrapper: `./gradlew <task>`.
- Minimum verification before merging: `./gradlew spotlessCheck`, `./gradlew test`, and `./gradlew spotbugsMain`.
- Use `./gradlew build` for full verification and keep tooling versions in sync with the configured Gradle toolchain.
- For Markdown files run `python scripts/format_markdown.py --check` (or `format_markdown.py` to auto-format).

## Testing expectations

- Author automated tests in Kotlin with JUnit 6, mirroring the package structure of the code under test.
- Maintain both unit tests (pure collaborators) and integration tests (real process execution or IO-heavy scenarios).
- Enforce deterministic execution: bound timeouts, avoid sleep-based synchronization, and use cross-platform guards when
  behavior differs per OS. Project-specific testing practices live in
  [`context/testing/strategy.md`](../../testing/strategy.md).

## Documentation, collaboration & releases

- Keep README files and architectural docs current with behavior and tooling changes.
- Capture implementation-specific notes in `context/knowledge-base/` and register new research in
  [`context/research/registry.md`](../../research/registry.md).
- Follow the collaboration checklist in [`context/workflow/collaboration.md`](../../workflow/collaboration.md) and
  consult [`context/workflow/releases.md`](../../workflow/releases.md) before tagging a release.
- Maintain a single proposed commit message in the repository root `.commit-message` file. Update it before ending a
  work session so it describes the cumulative changes since the last commit. The file is ignored from version control
  but serves as the assistant’s staging note for commit text.
