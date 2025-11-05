# iCLI Samples Module

This module collects executable samples that compare iCLI with other JVM process libraries (Apache Commons Exec,
zt-exec, NuProcess, JLine). Each scenario demonstrates Java and Kotlin call sites plus matching tests so we can validate
ergonomics before publishing documentation.

- **Scope.** Code lives in `com.github.ulviar.icli.samples.<scenario>.<approach>` where `<approach>` matches the library
  (`icli`, `commonsExec`, `ztExec`, `nuProcess`, `jline`). Scenario folders will contain both implementation snippets
  and the supporting tests (fake process + real tool).
- **Real tool baseline.** Scenario #1 will target `java -version`, which is available on every supported platform. Guard
  real-tool tests with JUnit tags or assumptions if the tool might be missing on CI hosts.
- **Dependencies.** The module already depends on the latest releases of Commons Exec (1.5.0), zt-exec (1.12), NuProcess
  (3.0.0), and JLine (3.30.6) so contributors can focus on sample code instead of wiring libraries later.
- **Publication.** The samples module is documentation-only: do not configure publishing or ship artifacts from it.

## Single-run harness

Use the shared types under `com.github.ulviar.icli.samples.scenarios.single` to add comparable samples quickly:

- `CommandInvocation` — immutable command line definition (argv, working directory, environment, timeout, merge flag).
- `SingleRunExecutor` — adapter interface; use `SingleRunExecutors.defaultExecutors()` (Kotlin) to obtain both iCLI
  adapters (Essential + Advanced) alongside the Commons Exec, zt-exec, NuProcess, and JLine implementations.
- `ScenarioExecutionResult` — captures exit code, stdout/stderr text, duration, timeout flag, and any exception.
- Fake commands live in `FakeSingleRunProcess`; construct invocations via `FakeSingleRunInvocations.success(...)`.
- Real-tool helpers (starting with `java -version`) are exposed by `RealToolInvocations`.
- Tests live under `samples/src/test/kotlin` and assert that every adapter succeeds on the fake process and `java
  -version`.

## Running the samples build

Use Gradle via the MCP helpers from the repository root:

```bash
python scripts/run_mcp.py execute_gradle_task --tasks build --project :samples
```

or, from an IDE/terminal with Gradle available:

```bash
./gradlew :samples:build
```

Tests live under `samples/src/test/kotlin` and default to JUnit 6 with Kotlin assertions:

```bash
./gradlew :samples:test
```

## Adding a scenario

1. Create a new package under `com.github.ulviar.icli.samples.<scenarioName>` with `icli` and competitor subpackages.
2. Provide matching Java and Kotlin entry points for every implementation, sharing common helpers when possible.
3. Add two tests: one against a fake process/fixture (placed alongside the scenario) and one against the real CLI tool.
4. Update [samples/README.md](/samples/README.md) with the new scenario, required tools, and any extra setup steps.
5. Document guardrails for the real-tool test (JUnit tag, assumption, or Gradle profile) so CI remains deterministic.
