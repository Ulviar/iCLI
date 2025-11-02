# Testing Strategy

This strategy keeps verification predictable for both human contributors and Codex-style assistants. Tests should prove
the documented behaviour of each module, cover boundary and failure scenarios, and remain portable across supported
platforms.

## 1. Languages, frameworks, structure
- Write automated tests in Kotlin with JUnit 6; place them under `src/test/kotlin` mirroring the production package
  tree.
- Group additional suites (e.g., integration, smoke) under dedicated source sets such as `src/integrationTest/kotlin`;
  document new source sets in the task dossier and reference them from the project overview.
- Keep helper utilities and matchers colocated with the suites that own them. If a fixture is reused broadly, move it to
  a shared test-support package and link it back here.

## 2. Coverage categories
- **Unit tests:** Exercise a single collaborator with test doubles for external dependencies. Focus on validating
  invariants, error handling, and edge conditions derived from the API contract.
- **Integration tests:** Launch real processes, PTY-backed sessions, and pooled workers using deterministic fixtures.
  Verify IO, timeouts, cancellation, diagnostics, and platform-specific behaviours.
- **Contract/expect tests:** For expect-style flows, capture transcripts that prove prompt-response behaviour. Store
  fixtures under the owning test package with clear naming.

## 3. Fixtures and harnesses
- Maintain lightweight process fixtures (echo servers, deterministic REPLs) to avoid flaky external dependencies.
- For PTY coverage, provide helper builders that configure terminal size, encoding, and control signals; reuse them
  across suites.
- When adding a new fixture, document its purpose and reset behaviour in the owning test file and note it in the task
  dossier. If a fixture requires follow-up hardening, add a TODO with owner context plus a backlog entry.

## 4. PTY vs. non-PTY matrix
- Validate both PTY and pipe-based execution paths for every interactive feature. Structure tests so the same scenario
  can run under both configurations via parameterisation or shared assertions.
- Guard platform-specific expectations with JUnit tags or assumptions (`@EnabledOnOs`, `Assumptions.assumeTrue`) while
  still covering all supported platforms across the suite.
- Track Windows-specific coverage (ConPTY) separately until CI runs on Windows (see backlog item ICLI-011).

## 5. Timeouts, diagnostics, and resource safety
- Enforce explicit timeouts on tests that spawn processes or wait for asynchronous completions. Prefer deterministic
  synchronisation (futures, latches) over `Thread.sleep`.
- Assert diagnostic events, truncation flags, and error payloads where relevant to guarantee behaviour stays observable.
- Ensure tests close sessions, terminate processes, and clean up temporary files; record leaks as defects.

## 6. TDD and documentation alignment
- Derive scenarios directly from Javadoc/KDoc and published Markdown specs. Update the documentation first when gaps are
  discovered, then write failing tests before implementation.
- Capture the red/green cycle in the dossier execution history: list failing test names, the commands used, and the
  final passing run.

## 7. Reporting and automation
- Record every test command (including arguments and profiles) in dossier execution logs and PR descriptions.
- When suites are slow or platform-dependent, tag them appropriately and note the expected runtime or prerequisites so
  assistants can plan execution.
- Integrate new suites into Gradle (`run_gradle_tests`) and add them to quality gates when stable.
