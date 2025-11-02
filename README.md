# iCLI

iCLI is a JVM library (Java 25, Kotlin-friendly) that launches external commands, manages interactive sessions, and
supports worker pooling for expensive CLIs. This README introduces the public API as if the runtime is already
implemented, so we can evaluate how the design reads from a consumer perspective.

Project-wide AI assistant instructions live in [`AGENTS.md`](AGENTS.md).

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
ClientResult<String> result = service.run("--version");

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

ClientResult<String> shortForm = tunedService.run(builder ->
        builder
                .args("--version")
                .customizeOptions(options -> options.mergeErrorIntoOutput(true))
);

ClientResult<String> complex = tunedService.run(builder ->
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

---

## API Overview

### Client Package (`com.github.ulviar.icli.client`)

- `CommandService` — opinionated wrapper for a single console application; exposes `runner()`, `lineSessionRunner()`,
  and `interactiveSessionRunner()` built on shared defaults. Callers may work with `CommandCall` objects directly or
  supply a lambda that customises a `CommandCallBuilder` provided by the service.
- `CommandCall` / `CommandCallBuilder` — immutable snapshot of a prepared invocation and its fluent assembler for
  composing arguments, environment overrides, working directory, per-call option tweaks, and response decoder
  selection.
- `LineSessionRunner`, `InteractiveSessionRunner` — reusable launchers for interactive workflows; callers reuse their
  shared defaults and create new sessions on demand.
- `ClientResult<T>` — success/failure container; callers inspect `success()` then pull `value()` or `error()`.
- `ProcessExecutionException` — wrap for non-zero exits, exposing exit code plus truncated stdout/stderr.
- `InteractiveSessionClient`, `LineSessionClient` — convenience wrappers around raw interactive handles.
- `ResponseDecoder` & `LineDelimitedResponseDecoder` — strategies for turning interactive stdout into structured
  responses.

### Core Package (`com.github.ulviar.icli.core`)

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
4. For pooling, layer a planned `WorkerPool`/`CommandService` extension to amortise warmups when needed.

---

## Tooling

The project is built with the Gradle wrapper pinned to **Gradle 9.1.0**, and the Gradle toolchain provisions **JDK 25**.

---

## Project Guidelines

The canonical development workflow, coding standards, testing expectations, and release checkpoints are documented in
[`context/guidelines/general/contributor-guidelines.md`](context/guidelines/general/contributor-guidelines.md). Consult
that document before starting new work or reviewing changes.

---

## Context Library

Detailed background material, research history, roadmap notes, and project-specific conventions are organized under
[`context/`](context/). Begin with [`context/README.md`](context/README.md) to navigate the available references.
