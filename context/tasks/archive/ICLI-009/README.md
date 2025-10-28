# ICLI-009 — Implement PTY-backed interactive sessions

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-10-28
- **Owner:** Assistant (Codex)

## Overview
- **Objective:** Deliver PTY-capable interactive session support so `ProcessEngine.startSession` can launch long-lived
  commands under pipe or PTY transports and surface handles for higher-level clients.
- **Definition of Done:**
  - Implement `ProcessEngine.startSession` plus supporting runtime components for PTY and pipe-backed interactive
  processes per the execution architecture brief.
  - Introduce a `InteractiveSession`/client handle API with documented lifecycle controls (stdin/stderr/stdout streams,
  close/resize/signal helpers) ready for Essential API wiring.
  - Add Kotlin-based integration/unit tests covering PTY and non-PTY flows (happy path, idle/timeout, termination) using
  pty4j on supported platforms.
  - Update docs (architecture brief, knowledge base, or README snippets) if behaviour/defaults change.
  - Spotless/SpotBugs clean; all Gradle tests and relevant verification steps recorded in execution history.
- **Constraints:** Must reuse the pty4j dependency picked in ICLI-006, follow Java 25 + Spotless standards, enforce
  JetBrains nullability defaults, and honour roadmap requirements for soft-then-hard termination and diagnostics hooks.
- **Roles to notify:** Maintainer (per [`project-roles.md`](../../guidelines/icli/project-roles.md)).

## Planning
- **Scope summary:** Build the interactive session runtime: PTY-capable launcher, IO pumps, lifecycle supervisor, and
  public handle types, plus Minimal Essential API wiring stubs if needed.
- **Proposed deliverables:**
  - Java runtime code for `ProcessEngine.startSession`, PTY launcher integration, and session handle abstractions.
  - Kotlin/JUnit 6 test coverage (unit + integration) validating PTY vs pipe operation, idle timeout, shutdown flow, and
  diagnostics signals.
  - Documentation updates (architecture brief or knowledge base excerpt) summarising the new behaviour and configuration
  points.
- **Open questions / risks:**
  - Confirm whether PTY size defaults and TERM propagation are already centralised or need introduction here.
  - Determine approach for deterministic PTY tests in CI when PTY not available (guarded tests or simulation?).
  - Validate compatibility between pty4j license/config and our build (native libs packaging?).
- **Implementation strategy:**
  1. Extend the runtime launch layer with a PTY-capable `CommandLauncher` (pty4j-based) and selection logic honoring
  `TerminalPreference`/availability.
  2. Introduce an `InteractiveSessionHandle` implementation that wraps stdin/stdout/stderr plus lifecycle helpers,
  reusing `StreamDrainer` to fan process streams into user-available pipes and capturing exit futures.
  3. Teach `StandardProcessEngine.startSession` to orchestrate launch, pump wiring, shutdown escalation, and PTY
  cleanup, sharing `ShutdownExecutor` with the single-run path.
  4. Implement Kotlin/JUnit tests covering pipe vs PTY sessions, soft/hard termination, and resource cleanup, following
  TDD by driving behaviour from interaction specs first.
- **Design notes:**
  - Expand `InteractiveSession` interface to align with the architecture brief (`closeStdin`, `sendSignal`,
  `resizePty`). `InteractiveSessionClient` and test doubles (e.g., `ScriptedInteractiveSession`) must follow suit.
  - Model PTY-specific controls behind a small `TerminalControl` helper so pipe-backed sessions can no-op while PTY
  sessions invoke pty4j’s `setWinSize`/control-byte helpers.
  - Expose `Process.onExit()` futures directly for `onExit()` and guard shutdown with the existing `ShutdownExecutor` so
  the same soft→hard escalation logic applies when closing interactive handles.
- **Backlog link:** `[context/tasks/backlog.md](../backlog.md)`

## Analysis
- **Log entries:**
  - `analysis/2025-10-26.md`
- **Knowledge consulted:** Documented in the 2025-10-26 analysis log (roadmap Phase 4, execution architecture brief, PTY
  knowledge base).
- **Readiness decision:** Execution cleared after resolving PTY launcher selection/testing strategy.

## Research
- **Requests filed:** None.
- **External outputs:** _N/A_
- **Summary:** _N/A_
- **Human response:** _N/A_

## Execution
- **History entries:** [2025-10-26](execution-history/2025-10-26.md); [2025-10-28](execution-history/2025-10-28.md)
- **Implementation highlights:** Added PTY-aware launcher wiring, interactive session handle extensions, idle timeout
  supervision, and Kotlin test coverage spanning pipe + PTY flows (see execution logs).
- **Testing:** `gradle test`; `gradle integrationTest`
- **Follow-up work:** Track ConPTY coverage and diagnostics bus evolution via backlog items ICLI-010 and ICLI-011.
- **Retrospective:** PTY-backed sessions are now the default capability with graceful fallbacks; remaining work is
  incremental polish (observability breadth and Windows automation).

## Completion & archive
- **Archive status:** Archived (2025-10-28)
- **Archive location:** `context/tasks/archive/ICLI-009/`
- **Final verification:** `gradle test`; `gradle integrationTest`; `python scripts/pre_response_checks.py`

## Decisions & notes
- **Key decisions:** Default interactive launches to PTY when available with automatic fallback to pipes and lifecycle
  supervision via `ProcessInteractiveSession`.
- **Risks:** Windows ConPTY coverage still depends on follow-up automation (ICLI-011); monitor native dependency
  packaging as Kotlin modules arrive.
- **Links:** [`context/roadmap/execution-architecture-brief.md`](../../roadmap/execution-architecture-brief.md);
  [`context/tasks/backlog.md`](../backlog.md)
