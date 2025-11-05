# Samples Module Agent Guidelines

## Scope
- The `samples` module hosts executable code snippets that compare iCLI with other JVM process libraries; it is not
  published to Maven Central or included in release automation.
- Keep scenarios focused on ergonomics and working code; benchmarking lives elsewhere.

## Tooling & structure
- Continue using Java 25 for production samples and Kotlin + JUnit 6 for tests.
- Annotate packages with `@NotNullByDefault` and favour immutable helpers just like the core module.
- Organise code under `com.github.ulviar.icli.samples.<scenario>.<approach>` where `<approach>` is one of `icli`,
  `commonsExec`, `ztExec`, `nuProcess`, or `jline` depending on the implementation. The `icli` subpackage hosts both
  Essential and Advanced API samples.

## Workflow expectations
- Every scenario must provide both Java and Kotlin entry points plus two tests: one against a fake process fixture and
  one against a real CLI tool. Guard the real-tool test with JUnit tags/assumptions so CI stays stable.
- Scenario #1 standardises on `java -version` as the cross-platform CLI command; future scenarios should document their
  own tool prerequisites inside the module README.
- Do not add competitor dependencies to the core module; keep them scoped to `samples` so the main artifact remains
  lean.
- Reuse the shared harness under `com.github.ulviar.icli.samples.scenarios.single` (`CommandInvocation`,
  `SingleRunExecutor`, adapters, and Kotlin helpers) instead of duplicating process-launch logic per scenario.

## Documentation
- Update [samples/README.md](/samples/README.md) whenever you add a scenario or change the contribution workflow.
- Cross-link relevant roadmap/use-case catalogue entries so readers understand which scenario a sample illustrates.
