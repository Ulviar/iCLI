# Session Completion Checklist

Run through every step below before ending a work session. Skipping any item risks leaving the repository in a
non-compliant state.

1. **Inspect the working tree.** Run `git status -sb` (read-only) to understand which files changed and whether they
   include source code, Markdown, or both.
2. **When source code changed (Java/Kotlin/scripts/etc.):**
   - Format the code via `execute_gradle_task` with `tasks: ["spotlessApply"]` (add other Spotless targets if needed).
   - Execute the relevant Gradle test suites using `run_gradle_tests` (e.g., `gradleTasks: ["test"]` or targeted tasks).
   - Rerun formatting/tests until they pass and log the commands plus outcomes in the task dossier.
3. **When Markdown files changed:**
   - Run `python scripts/format_markdown.py <FILE ...>` on every modified `.md` file (or `--check` once the files are
     formatted) to enforce the repository’s wrapping rules.
   - Capture the formatter command(s) and results in the execution log.
4. **Update the commit summary.** Refresh `.commit-message` so it reflects the entire current diff since the last
   commit (single proposed message per repository guidelines).
5. **Final verification:** re-run `git status -sb` to confirm only the intended files remain staged/unstaged, then note
   in the dossier that the checklist was completed.
