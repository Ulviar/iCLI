# Session Completion Checklist

Run through every step below before ending a work session. Skipping any item risks leaving the repository in a
non-compliant state.

1. **Run the automated gate.** Execute `python scripts/pre_response_checks.py`. Follow every instruction it prints:
   this covers `git status`, canonical change detection (`scripts/list_changed_files.py`), Markdown formatting, and
   verification that `.commit-message` is populated.
2. **Trigger Gradle workflows via MCP.** When the script indicates source files changed, run
   `execute_gradle_task` with `tasks: ["spotlessApply"]` and `run_gradle_tests` with the suites it lists (at minimum
   `["test"]`). Repeat until they pass, logging each command and result in the dossier.
3. **Review coding standards.** After the Gradle tasks succeed, inspect the diffs for compliance with
   `context/guidelines/coding/standards.md`, calling out any intentional exceptions in the execution log.
4. **Refresh the commit summary.** Rewrite `.commit-message` so it reflects the entire current diff since the last
   commit—replace any previous content so only one proposed message (with optional bullet list) remains. If the script
   flagged issues, resolve them before moving on.
5. **Confirm cleanliness.** Re-run `python scripts/pre_response_checks.py` to ensure no new issues appeared, then note
   in the dossier that the checklist was completed.
