# Kotlin Legacy Solution Audit

## Scope
Review of the Kotlin implementation under `context/knowledge-base/archive/old` with focus on the Phase 1 roadmap task
“Audit existing Kotlin solution to identify reusable ideas, pain points, and missing capabilities.”

## Reusable Ideas
- `InteractiveCommandExecutor` keeps a warm process for repeated invocations and caps reuse with
  `MAX_COMMAND_BEFORE_RESTART` (see `InteractiveCommandExecutor.kt:28`), which is useful for amortizing expensive
  startups while still mitigating leaks.
- The executor pool builds on Apache Commons Pool (`InteractiveCommandExecutorPool.kt:15`), giving us an off-the-shelf
  eviction model and bounded concurrency that we can reapply with a modernized worker implementation.
- `TimeoutScheduler` wraps `CompletableFuture` with an internal scheduler (`TimeoutScheduler.kt:15`) to avoid
  `orTimeout` side effects; a similar adapter could shield us from test utilities that flag static executors.

## Pain Points
- Output collection relies on `BufferedReader.ready()` busy loops with `Thread.sleep(0, 100)`
  (`InteractiveCommandExecutor.kt:104`), so commands that emit partial lines or binary data can hang indefinitely; we
  also lose line separators because `readLine()` strips them.
- `InteractiveCommandExecutor` hardcodes a single command/argument pair per process and throws for any mismatch
  (`InteractiveCommandExecutor.kt:58`), blocking use cases that need to send heterogeneous commands through one session.
- The interactive writers/readers close around platform default charsets and always append `newLine()`
  (`InteractiveCommandExecutor.kt:70`), making it impossible to deliver raw bytes or control encoding.
- `ActiveProcess` constructs fixed thread pools with virtual thread factories (`ActiveProcess.kt:22`), an anti-pattern
  that keeps virtual threads parked on carrier threads and defeats the resource savings they aimed for.
- `CommandExecutionResult` mixes null checks and Russian error messages (`CommandExecutionResult.kt:6`), so downstream
  callers need defensive null handling and localization.
- Process restarts only trigger after 10 000 commands and never on failure (`InteractiveCommandExecutor.kt:91`),
  allowing stuck sessions to poison the pool until manual stop.

## Missing Capabilities
- No PTY/ConPTY integration; interactive behavior depends on `cat`/`more` shims (`InteractiveCommand.kt:6`), so real
  REPLs or TUIs will not operate correctly.
- APIs lack ways to set working directories, environment overrides, or resource limits—`ProcessBuilder` is always
  constructed with defaults (`SingleCommandExecutor.kt:14`, `InteractiveCommandExecutor.kt:96`).
- There is no streaming handle or callback model; callers must wait for full command completion, and stderr is only
  surfaced as a string (no incremental consumption or structured diagnostics).
- Pooling is bound to a single command definition; sharing pooled workers across different binaries or argument sets is
  unsupported.
- Tests cover happy-path echo workflows but skip cross-platform, error propagation, PTY, large output, and cancellation
  scenarios, leaving major behaviors unverified.

## Additional Observations
- Single-shot execution (`SingleCommandExecutor.kt:27`) trims output, which destroys meaningful trailing whitespace.
- Timeout handling returns generic messages without propagating root causes, complicating troubleshooting.
