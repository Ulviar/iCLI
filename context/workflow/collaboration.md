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
