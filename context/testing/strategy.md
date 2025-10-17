# Testing Strategy

- Write automated tests in Kotlin with JUnit 6 and align package structures with the production code they cover.
- Categorize coverage into unit tests (pure collaborators with fakes) and integration tests (real process execution with
  deterministic fixtures).
- Validate both PTY and non-PTY paths for interactive features; add OS guards when behavior diverges.
- Enforce timeouts for any test that spawns processes to prevent hangs and document slow-running cases with JUnit tags.
