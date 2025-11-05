# Process Fixture Module Agent Guidelines

## Scope
- The `process-fixture` module packages the standalone CLI used to exercise iCLI integration and stress scenarios.
- It must stay deterministic when seeded so other modules can rely on it for automated tests.

## Tooling & language
- Production sources are Java 25 with package-level `@NotNullByDefault` annotations; tests are Kotlin 2.2.20 with JUnit
  6.
- Keep dependencies minimal (only JetBrains annotations + SpotBugs annotations by default) so downstream integration
  stays lightweight.

## Workflow expectations
- Expose a single entry point `com.github.ulviar.icli.fixture.ProcessFixture` that honours CLI flags defined in
  [process-fixture-spec.md](/context/roadmap/process-fixture-spec.md).
- Add docs and helpers whenever new modes or commands are introduced so other modules can discover them easily.
- Follow TDD: write Kotlin tests for new fixture behaviours before modifying the Java implementation.
- Keep random behaviour reproducible: every stochastic mode must accept a seed and document the default.

## Documentation
- Update [README.md](/process-fixture/README.md) whenever the CLI surface changes (new flags, commands, or telemetry
  fields).
- Note any intentionally platform-specific behaviour or limitations in the README so callers can add matching guards in
  their tests.
