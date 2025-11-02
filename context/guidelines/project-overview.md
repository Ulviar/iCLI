# Project Guidelines Overview

Use this document as the single entry point for iCLI's standards. It packages the information Codex-style assistants
need to stay within context windows while still guiding human contributors. Each section links to the authoritative
source and highlights why it matters for assistant-led development of a modular, SOLID- and GRASP-aligned codebase.

## 1. Source structure & ownership
- **Repository governance:** See [AGENTS.md](/AGENTS.md) for toolchain requirements, nullability policy, and Gradle
  invocation rules.
- **General layout:** [contributor-guidelines.md](/context/guidelines/general/contributor-guidelines.md) defines
  production vs. test directories and outlines writing documentation inside `context/`.
- **iCLI specifics:** [project-conventions.md](/context/guidelines/icli/project-conventions.md) explains package
  prefixes, PTY usage defaults, and virtual-thread expectations.
- **Modularity principles:** [assistant-notes.md](/context/guidelines/icli/assistant-notes.md) captures the preferred
  decomposition patterns (records for immutable data, assistant-centric TDD ritual) that keep component scopes small
  enough for reliable AI collaboration.

## 2. Coding standards
- **Mandatory rules:** [coding/standards.md](/context/guidelines/coding/standards.md) is the canonical reference for
  Palantir Java Format, Kotlin conventions, nullability, immutability, and record usage.
- **Design heuristics:** Reinforce SOLID and GRASP principles in every change; favour small, single-purpose classes and
  explicit collaborator boundaries so assistants can reason locally.
- **Documentation:** Class-level Javadoc/KDoc is compulsory; tie behaviour descriptions directly to tests so assistants
  can derive coverage without reading implementation details.

## 3. Build, tooling, and automation
- **Gradle usage:** All builds run through MCP tasks (`execute_gradle_task`, `run_gradle_tests`); never call `./gradlew`
  from the shell. See [AGENTS.md](/AGENTS.md) for the rule and versions (Gradle 9.1.0, Java 25, Kotlin 2.2.20).
- **Quality gates:** Minimum verification includes `spotlessCheck`, `spotbugsMain`, and `test`. Capture the executed
  commands in dossier execution logs.
- **Automation scripts:** `scripts/pre_response_checks.py` must run before handing work back to the maintainer. The
  script reports repo status, changed files (via `list_changed_files.py`), and markdown formatting hints.
- **Follow-up tooling:** When altering build or formatting configuration, consult
  [workflow/quality-gates.md](/context/workflow/quality-gates.md) for the full automation checklist and update this
  overview if new requirements arise.

## 4. Testing expectations
- **Strategy baseline:** [testing/strategy.md](/context/testing/strategy.md) explains unit vs. integration coverage, PTY
  matrix requirements, and timeout discipline.
- **Fixtures & harnesses:** Document reusable fixtures alongside tests; when creating new ones add cross-links here and
  to the testing strategy. If a fixture is missing, record a TODO with owner context and raise a backlog item.
- **TDD workflow:** Tests precede implementation. When documentation lacks detail, update the relevant section before
  writing code so expectations stay explicit.
- **Cross-platform goals:** Integration suites must consider Linux, macOS, and Windows (ConPTY). Track gaps in the
  backlog (e.g., planned Windows CI enablement under ICLI-011).

## 5. Documentation practices
- **Living knowledge:** [context/README.md](/context/README.md) and the [context overview](/context/context-overview.md)
  describe how knowledge is organised. Follow the overview before every task to refresh mandatory materials.
- **Roadmap alignment:** [project-roadmap.md](/context/roadmap/project-roadmap.md) and its companion briefs (execution
  requirements, architecture, use cases) drive prioritisation. Update them when behaviour or scope shifts.
- **Task history:** Maintain dossiers in `context/tasks/` per [tasks/README.md](/context/tasks/README.md). Every
  delivery must log planning, analysis, execution, and retrospectives so future assistants can reconstruct decisions
  quickly.
- **Assistant-first clarity:** Prefer concise summaries, link dense sources, and call out assumptions to keep context
  manageable for AI collaborators.

## 6. Collaboration workflow
- **Branching & reviews:** [workflow/collaboration.md](/context/workflow/collaboration.md) lists branch naming,
  pull-request evidence, and Definition of Done expectations.
- **Release readiness:** Track release steps in [workflow/releases.md](/context/workflow/releases.md); expand it as the
  distribution process matures.
- **Commit staging:** Maintain `.commit-message` at the repo root with a single proposed message covering all pending
  changes; refresh it whenever the diff changes.
- **Session close-out:** Follow [checklists/session-completion.md](/context/checklists/session-completion.md) before
  ending a task. Record completion inside the active dossier.

## 7. Process improvement & future work
- **Guidelines rollout tracker:** See [../checklists/guidelines-rollout.md](/context/checklists/guidelines-rollout.md)
  for the status of guideline initiatives and outstanding backlog items.
- **Assistant needs:** When new documents emerge, update this overview and the rollout checklist simultaneously to keep
  navigation reliable.
- **Research linkage:** Register studies in [research/registry.md](/context/research/registry.md) so assistants can
  discover experiments relevant to forthcoming tasks.

**Reminder for assistants:** Treat this overview as the navigation map, but always read the linked documents before
implementation. Capture deviations or ambiguities in the active task dossier to maintain the feedback loop that keeps
iCLI documentation accurate and assistant-friendly.
