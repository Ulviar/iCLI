# Contributor Guidelines Overview

This file distils cross-project expectations for Java/Kotlin libraries built with Gradle. Start with the [Project
Guidelines Overview](../project-overview.md) for navigation, then use the sections below to clarify day-to-day
behaviour. Project-specific rules continue to live under [`context/guidelines/icli/`](../icli).

## 1. Orientation

- Read the root [AGENTS.md](../../../AGENTS.md) before contributing and add scoped agents when new modules demand extra
  constraints.
- Treat [context/README.md](../../README.md) and [context/context-overview.md](../../context-overview.md) as the
  knowledge entry points. The overview lists mandatory documents to revisit at the start of every session.
- Follow the assistant-managed workflow described in [context/tasks/README.md](../../tasks/README.md); maintain task
  dossiers so history stays discoverable.
- Respect the single-maintainer model in [project-roles.md](../icli/project-roles.md) and keep all documentation in
  English.
- Apply the Markdown rules in [markdown-formatting.md](markdown-formatting.md) when editing `.md` files.

## 2. Source structure & ownership

- Keep production Java sources in `src/main/java` and supporting resources in `src/main/resources`.
- Author tests in Kotlin under `src/test/kotlin`, storing fixtures near their owning tests unless reused broadly.
- Place long-form documentation inside `context/` so architectural history remains centralised.
- When introducing new top-level directories or modules, add a scoped [AGENTS.md](../../../AGENTS.md) and reference it
  from the project overview to help assistants discover the rules.
- Design packages around SOLID/GRASP boundaries—prefer small collaborators over monoliths to keep context slices small
  enough for Codex-style assistants.

## 3. Coding standards

- Target Java 25 in production and adhere to [coding/standards.md](../coding/standards.md) for Palantir Java Format,
  Spotless enforcement, nullability, records, and immutability.
- Kotlin code follows the official conventions enforced via Spotless/ktlint; keep tests idiomatic Kotlin.
- Default to JetBrains `@NotNullByDefault` and only annotate `@Nullable` where null is intentional.
- Document concurrency decisions and behavioural contracts with Javadoc/KDoc so tests (and assistants) derive scenarios
  without reading implementations.

## 4. Build, tooling, and automation

- Invoke Gradle exclusively through the MCP tools (`execute_gradle_task`, `run_gradle_tests`); never call `./gradlew`
  directly from the shell.
- Minimum verification for any change: `spotlessCheck`, `test`, and `spotbugsMain`. Record executed commands in the
  active task dossier.
- Use the aggregated `build` task for release-ready validation. Keep tooling versions aligned with
  [AGENTS.md](../../../AGENTS.md).
- Run `python scripts/format_markdown.py --check` for Markdown validation; use the write mode if formatting fixes are
  required.
- Execute `scripts/pre_response_checks.py` before handing off work to surface status, changed files, and formatting
  reminders—this is mandatory for assistants.

## 5. Testing expectations

- Follow the [testing strategy](../../testing/strategy.md) for unit vs. integration coverage, PTY vs. non-PTY matrices,
  timeout discipline, and cross-platform goals.
- Write tests in Kotlin with JUnit 6, mirroring production package structure. Favour fine-grained tests that prove
  documented behaviour (happy paths, error handling, edge cases).
- Build or extend reusable fixtures when coverage demands them; document each fixture and link it from the testing
  strategy and relevant task dossier. If a fixture is pending, capture a TODO with owner/context and open a backlog
  entry.
- Practise strict TDD: update docs/specs, write failing tests, implement minimal code, and rerun the suite until green.

## 6. Documentation, collaboration & releases

- Update architecture docs, READMEs, and knowledge base entries in lockstep with code changes. Register new research via
  [context/research/registry.md](../../research/registry.md).
- Follow [workflow/collaboration.md](../../workflow/collaboration.md) for branch naming, PR evidence, and Definition of
  Done expectations. Add automation checkpoints to execution logs.
- Consult [workflow/releases.md](../../workflow/releases.md) before tagging a release and record platform validation
  results.
- Maintain a single proposed commit message in `.commit-message` at the repository root; refresh it whenever the diff
  changes.
- Close each session by running the checklist in
  [context/checklists/session-completion.md](../../checklists/session-completion.md) and logging completion inside the
  active task dossier.
