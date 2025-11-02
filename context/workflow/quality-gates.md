# Quality Gates & Automation

This guide clarifies the verification steps every contribution must run before hand-off. It is written for AI
assistants, but human contributors should follow the same checklist to guarantee production-ready outcomes.

## 1. Mandatory commands
- `python scripts/pre_response_checks.py`: Run before every hand-off. The script reports repository status, changed
  files (via `scripts/list_changed_files.py`), and markdown formatting hints. Rerun after making additional edits.
- `execute_gradle_task(["spotlessCheck"])`: Ensures Palantir Java Format and ktlint rules are satisfied.
- `execute_gradle_task(["test"])`: Runs the Kotlin/JUnit 6 suites for the affected source sets. Include additional test
  tasks (e.g., `["integrationTest"]`) when relevant source sets change.
- `execute_gradle_task(["spotbugsMain"])`: Executes static analysis for the Java sources. Run `spotbugsTest` when test
  packages introduce custom Java helpers.
- `python scripts/format_markdown.py --check`: Validate Markdown formatting prior to committing. Use the write mode if
  formatting fixes are required.

Invoke Gradle exclusively through the MCP tools as required by [AGENTS.md](../../AGENTS.md); never call `./gradlew` from
the shell.

## 2. Recording evidence
- Log every command and outcome in the active task dossier's execution history. Include timestamps, tool arguments, and
  whether the run produced failures.
- For TDD cycles, capture the failing test names before implementation and note the passing rerun.
- When skipping a gate (e.g., due to platform limitations), document the justification and open a backlog item if a
  permanent solution is required.

## 3. Pull request expectations
- Summarise verification steps in the PR description, mirroring the dossier entries.
- Provide links to relevant task dossiers and knowledge base updates so reviewers can trace decisions quickly.
- Highlight any follow-up actions (new backlog items, TODOs with owners) that arose during quality checks.

## 4. Continuous improvement
- Update this guide whenever new tooling or CI jobs are added. Reflect the change in the [Project Guidelines
  Overview](../guidelines/project-overview.md) and the guidelines rollout checklist.
- Treat missing or flaky automation as defects; capture them in [backlog.md](../tasks/backlog.md) and prioritise fixes
  to keep assistant workflows reliable.
