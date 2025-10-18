# Collaboration Workflow

- Use feature branches with the prefix `feature/`, maintenance branches with `chore/`, and bug fixes with `fix/`.
- Keep commits scoped to a single logical change and phrase messages in the imperative mood.
- Every pull request must include: a summary, testing evidence (commands plus status), and follow-up items if work
  remains.
- Definition of Done: code, tests, and documentation updated together; required Gradle checks succeed; relevant context
  files are refreshed.
- Maintain per-task dossiers under `context/tasks/` using the assistant-managed workflow described in
  [`context/tasks/README.md`](../tasks/README.md); ensure analysis, research, and execution logs are current before
  opening a pull request.
- Before changing build tooling or dependencies, verify the latest stable version from the official distribution
  (e.g., Gradle Plugin Portal or Maven Central), record the source in commit notes if the version changes, and rerun the
  relevant Gradle tasks to confirm the update succeeds.
