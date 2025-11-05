# iCLI

iCLI is a JVM library (Java 25, Kotlin-friendly) that launches external commands, manages interactive sessions, and
supports worker pooling for expensive CLIs. This README introduces the public API as if the runtime is already
implemented, so we can evaluate how the design reads from a consumer perspective.

Project-wide AI assistant instructions live in [AGENTS.md](/AGENTS.md).

## Samples module

Hands-on usage examples now live in the `samples` Gradle module. Each scenario will showcase iCLI side-by-side with
Apache Commons Exec, zt-exec, NuProcess, and JLine implementations so we can verify ergonomics before publishing guides.
Scenario #1 standardises on `java -version` as the real CLI baseline. See [samples/README.md](/samples/README.md) for
contributor guidance and build instructions (`./gradlew :samples:build`).

---

## Quick Start

Add the dependency (coordinates TBD once publishing is configured) and set up your execution engine implementation. The
simplest path is to inject an engine when constructing the high-level clients.

```java
var engine = new StandardProcessEngine(); // default engine selects pipes or PTY automatically
var command = CommandDefinition.builder()
        .command(List.of("python"))
        .build();
var service = new CommandService(engine, command);
```

### Run a one-off command

```java
CommandResult<String> result = service.runner().run(builder -> builder.args("--version"));

if (result.success()) {
    System.out.println(result.value());
} else {
    throw result.error();
}
```

`CommandService` provides bounded output capture (stdout 64 KiB, stderr 32 KiB) with a soft interrupt timeout of 60
seconds followed by a 5 second hard kill. Adjust per invocation via the builder or construct the service with custom
`ExecutionOptions` defaults.

### Drive an interactive REPL

```java
LineSessionRunner pythonRunner = service.lineSessionRunner();

try (var python = pythonRunner.open(builder -> builder.args("-i"))) {
    var response = python.process("print(6 * 7)");
    System.out.println(response.value()); // -> "42"

    python.closeStdin();
}
```

`LineSessionClient` serialises requests internally so concurrent callers cannot interleave writes/reads accidentally.
Reuse the runner whenever you need new sessions and switch to `session.interactive()` when raw stream access or control
signals are required.

### Script expect-style interactions

`LineSessionRunner` exposes the `LineExpect` DSL for prompt/response automation. The helper wraps the active
`LineSessionClient`, reuses your decoder, and lets you describe conversations with `send` / `expect` calls while
honouring runner defaults (timeouts, scheduler):

```java
LineSessionRunner replRunner = service.lineSessionRunner();

try (LineSessionClient session = replRunner.open(builder -> builder.args("-i"));
        LineExpect expect = session.expect().withDefaultTimeout(Duration.ofSeconds(2))) {
    expect.expectMatches(Pattern.compile("Python .*"));
    expect.sendAndExpect("print(6 * 7)", "42");
    expect.closeStdin();
}
```

Failures raise `LineExpectationException` (or `LineExpectationTimeoutException` when the deadline elapses) so test
suites can assert on transcripts without parsing stdout manually. Use this path for catalogue scenarios that require
scripted dialogs (credential prompts, expect scripts, REPL-driven automation).

### Reuse warmed workers

`CommandService.pooled()` returns a `PooledCommandService` that mirrors the standard runners but drives a `ProcessPool`
behind the scenes. Each helper owns its pool and implements `AutoCloseable`, so use try-with-resources to scope worker
lifecycles:

```java
PooledCommandService pooled = service.pooled();

try (PooledCommandRunner runner = pooled.commandRunner(spec -> spec.maxSize(4))) {
    CommandResult<String> result = runner.process("version");
    if (!result.success()) {
        throw result.error();
    }
}
```

Need raw access to a pooled session or to integrate with custom diagnostics? Ask the facade for the advanced client and
work with `ProcessPoolClient`, `ServiceProcessor`, or `ServiceConversation` directly:

```java
PooledCommandService pooled = service.pooled();

try (ProcessPoolClient client = pooled.client(PooledClientSpec::defaultSpec)) {
    ServiceConversation conversation = client.openConversation();
    conversation.line().process("print('ready')");
    conversation.retire(); // replace the worker before returning to the pool
}
```

This keeps the Essential API front and centre while still exposing the low-level pool controls required for specialised
scenarios.

### Advanced: customise execution

The low-level `core` package exposes building blocks.

```java
CommandDefinition command = CommandDefinition.builder()
        .command(List.of("/usr/bin/env"))
        .putEnvironment("MODE", "diagnostics")
        .terminalPreference(TerminalPreference.DISABLED)
        .build();

ExecutionOptions options = ExecutionOptions.builder()
        .stdoutPolicy(OutputCapture.streaming())
        .stderrPolicy(OutputCapture.bounded(8 * 1024))
        .mergeErrorIntoOutput(true)
        .shutdownPlan(new ShutdownPlan(Duration.ofSeconds(10), Duration.ofSeconds(2), ShutdownSignal.TERMINATE))
        .destroyProcessTree(true)
        .build();

CommandService tunedService = new CommandService(engine, command, options);

CommandResult<String> shortForm = tunedService.runner().run(builder ->
        builder
                .args("--version")
                .customizeOptions(options -> options.mergeErrorIntoOutput(true))
);

CommandResult<String> complex = tunedService.runner().run(builder ->
        builder
                .subcommand("run")
                .option("--rm")
                .args("alpine", "echo", "hi")
                .env("MODE", "test")
                .workingDirectory(Path.of("/sandbox"))
);
```

The `ProcessEngine` implementation is responsible for honouring PTY requests, wiring IO bridges, enforcing timeouts, and
producing a `ProcessResult`.

## Scenario cheat sheet

Each Essential API runner targets scenarios from the
[execution-use-case-catalogue](/context/roadmap/execution-use-case-catalogue.md). Use this table to select the right
helper and understand how pooled facades slot in today versus upcoming scenario-specific runners:

| Catalogue scenario                                      | Standard helper(s)                                                                              | Pooled helper(s)                                                                                  | Notes / next steps                                                                                                                                                     |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CLI tooling automation & environment diagnostics        | `CommandService.runner()` / `service.runner().run(...)`                                          | `PooledCommandRunner` via `service.pooled().commandRunner(...)`                                   | Covers single-run automation with bounded capture + timeouts. Use pooling when command warmups dominate throughput.                                                   |
| Line-oriented REPL automation & scripted expect flows   | `LineSessionRunner` + `LineSessionClient.expect()` / `LineExpect`                                | `PooledLineSessionRunner`                                                                         | Powers prompt/response workflows (credential prompts, expect scripts). Each pooled runner owns its pool; scope instances with try-with-resources.                     |
| Full interactive sessions with raw stream control       | `InteractiveSessionRunner` + `InteractiveSessionClient`                                         | `PooledInteractiveSessionRunner`                                                                  | Use for PTY-backed shells, streaming IO, and long-lived sessions that still benefit from Essential ergonomics.                                                         |
| Stateful pooled conversations                           | Direct sessions via `CommandService` when a single worker suffices                               | `ServiceConversation` / `PooledLineConversation` via `service.pooled().client(...)`               | Advanced workflows that retain session state across requests should drop to `ProcessPoolClient`. Affinity + reset helpers arrive with ICLI-025.                      |
| Listen-only monitoring (tail, log follow)               | `listenOnlyRunner()` + `ListenOnlySessionClient` streaming publishers                            | `PooledListenOnlySessionRunner` or `ProcessPoolClient`                                            | Clients subscribe to stdout/stderr publishers for reactive tailing. Kotlin Flow adapters ship in a separate module; use the raw `Flow.Publisher<ByteBuffer>` surface here. |
| CLI-backed MCP tools/resources                          | `CommandRunner` / `LineSessionRunner` (per tool semantics)                                       | `PooledCommandRunner` or advanced `ProcessPoolClient` for hot adapters                            | Scenario templates + ready-made MCP adapters will be published via ICLI-024.                                                                                           |

Scenario presets (TBD-001) will later bundle opinionated `ExecutionOptions`/`ProcessPoolConfig` defaults so a single
method call can provision each runner for its target scenario.

---

## Supported Packages

- **Essential API (public):** `com.github.ulviar.icli.client`, `com.github.ulviar.icli.client.pooled`
- **Advanced API (public):** `com.github.ulviar.icli.engine`, `com.github.ulviar.icli.engine.runtime`,
  `com.github.ulviar.icli.engine.pool.api`, `com.github.ulviar.icli.engine.pool.api.hooks`
- **Diagnostics API (public):** `com.github.ulviar.icli.engine.diagnostics`
- **Internal (not exported):** `com.github.ulviar.icli.engine.runtime.internal.*`,
  `com.github.ulviar.icli.engine.pool.internal.*`

These package boundaries align with the Essential/Advanced split described in the architecture brief. The
`com.github.ulviar.icli.client.pooled` package hosts both the Essential `PooledCommandService` facade and the advanced
`ProcessPoolClient`/`Service*` collaborators; the facade should be your starting point, dropping to the advanced helpers
only when you need to orchestrate pools yourself. Consumers should rely on the exported packages above; everything under
`internal` namespaces is subject to change without notice.

---

## API Overview

### Client Package (`com.github.ulviar.icli.client`)

- `CommandService` — opinionated wrapper for a single console application; exposes `runner()`, `lineSessionRunner()`,
  `listenOnlyRunner()`, and `interactiveSessionRunner()` built on shared defaults. Callers may work with `CommandCall`
  objects directly or supply a lambda that customises a `CommandCallBuilder` provided by the service.
- `CommandCall` / `CommandCallBuilder` — immutable snapshot of a prepared invocation and its fluent assembler for
  composing arguments, environment overrides, working directory, per-call option tweaks, and response decoder selection.
- `LineSessionRunner`, `InteractiveSessionRunner` — reusable launchers for interactive workflows; callers reuse their
  shared defaults and create new sessions on demand.
- `CommandResult<T>` — success/failure container; callers inspect `success()` then pull `value()` or `error()`.
- `InteractiveSessionClient`, `LineSessionClient` — convenience wrappers around raw interactive handles.
- `ResponseDecoder` & `LineDelimitedResponseDecoder` — strategies for turning interactive stdout into structured
  responses.

### Pooled Client Package (`com.github.ulviar.icli.client.pooled`)

- `PooledCommandService` — mirrors `CommandService` but scopes helpers to worker pools; expose `commandRunner()`,
  `lineSessionRunner()`, `interactiveSessionRunner()`, and `client()` for advanced control.
- `PooledCommandRunner`, `PooledLineSessionRunner`, `PooledInteractiveSessionRunner` — helper types that borrow a pool
  per instance and expose the same ergonomics as their non-pooled counterparts.
- `PooledClientSpec` — fluent configurator for pool sizing, diagnostics listeners, and idle policies used by the pooled
  facade helpers.
- `ProcessPoolClient`, `ServiceProcessor`, `ServiceConversation`, `ServiceProcessorListener` — advanced collaborators
  for callers who need to orchestrate `ProcessPool` directly while retaining Essential API ergonomics.

### Core Package (`com.github.ulviar.icli.engine`)

- `CommandDefinition` — immutable launch descriptor (argv, working directory, environment, terminal preference, optional
  shell).
- `ExecutionOptions` — bounded output policies, merge behaviour, shutdown plan (soft interrupt, grace period, final
  signal), and process-tree destruction flag.
- `OutputCapture` — helper factory for bounded, streaming, or discard policies with charset configuration.
- `TerminalPreference` — PTY request hint (`AUTO`, `REQUIRED`, `DISABLED`).
- `ShellConfiguration` — shell invocation descriptor (command + invocation style).
- `ShutdownPlan` & `ShutdownSignal` — termination strategy.
- `ProcessResult` — command outcome structure (exit code, stdout/stderr snapshots, optional duration).
- `InteractiveSession` — low-level handle exposing streams and `onExit`.
- `ProcessEngine` — SPI implemented by the runtime; provides the actual process management implementation consumed by
  `CommandService`.

### Runtime Package (`com.github.ulviar.icli.engine.runtime`)

- `StandardProcessEngine` — default runtime implementation that selects pipe or PTY transports, supervises child
  processes, and surfaces results through `ProcessResult`.
- `ProcessEngineExecutionException` — signals operational failures while supervising a running process (e.g., stream
  drains, interruptions).
- `ProcessShutdownException` — indicates the configured shutdown plan failed to complete gracefully.

### Diagnostics Package (`com.github.ulviar.icli.engine.diagnostics`)

- `DiagnosticsListener` — callback interface for streaming output events and truncation notices.
- `DiagnosticsEvent` — sealed hierarchy describing emitted diagnostics payloads.
- `StreamType` — enum identifying stdout, stderr, or merged streams when reporting diagnostics.

### Testing Utilities (`com.github.ulviar.icli.testing`)

The module ships Kotlin test doubles (`RecordingExecutionEngine`, `ScriptedInteractiveSession`) to help with unit tests.

### Running tests

- `./gradlew test` — runs the default unit test suite.
- `./gradlew integrationTest` — runs optional integration tests that execute real shell commands. These tests are
  skipped by default so they can be invoked manually (e.g., on Windows to validate PTY/shell behaviour).

---

## Conceptual Workflow

1. Build a `CommandDefinition` capturing argv, working directory, environment, and terminal requirements.
2. Configure `CommandService` with your `ProcessEngine`, command definition, and optional `ExecutionOptions` defaults
   (applied to both single runs and sessions).
3. Use `CommandService.runner()` for single-shot invocations, and rely on `lineSessionRunner()` /
   `interactiveSessionRunner()` for long-lived interactions; tweak timeouts or capture policies per call via the
   provided customisers.
4. For pooling, call `CommandService.pooled()` to obtain `PooledCommandService` helpers (or
   `service.pooled().client(...)` for direct `ProcessPoolClient` access) and amortise warmups when needed.

---

## Tooling

The project is built with the Gradle wrapper pinned to **Gradle 9.1.0**, and the Gradle toolchain provisions **JDK 25**.

---

## Project Guidelines

The canonical development workflow, coding standards, testing expectations, and release checkpoints are documented in
[context/guidelines/general/contributor-guidelines.md](/context/guidelines/general/contributor-guidelines.md). Consult
that document before starting new work or reviewing changes.

---

## Context Library

Detailed background material, research history, roadmap notes, and project-specific conventions are organized under
[`context/`](/context). Begin with [context/README.md](/context/README.md) to navigate the available references.
