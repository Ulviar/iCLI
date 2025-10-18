# Execution Use Case Catalogue

## Purpose
- Document maintainer-sourced process execution scenarios and their primary constraints ahead of architecture work.
- Synthesise insights from the execution requirements brief, knowledge base, legacy audit, and roadmap as of
  2025-10-18.

## Single-run command use cases

### CLI tooling automation
**Scenario.** Run tools such as `git`, `java`, and packaging utilities with per-call arguments and environment tweaks
inside build and CI flows.

**Constraints.**
- Return structured results that surface exit codes, timings, and truncation flags so automation can assert success
  deterministically ([Execution requirements](execution-requirements.md)).
- Stream stdout and stderr incrementally with bounded capture, exposing both decoded text and raw bytes with explicit
  charsets to avoid data loss ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Prefer direct binary execution and fall back to shell wrapping only when necessary to avoid quoting issues
  ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).

**Sources.** [Execution requirements](execution-requirements.md);
[Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md).

### Environment diagnostics
**Scenario.** Issue short-lived discovery commands (`which`, `uname`, `java -version`) during setup and health checks
to confirm environment state ([Execution requirements](execution-requirements.md)).

**Constraints.**
- Support direct binary invocation across Linux, macOS, and Windows, respecting their quoting, path, and environment
  semantics ([Execution requirements](execution-requirements.md)).
- Propagate working directory overrides and honour Unicode paths so discovery steps can run in project-specific
  locations ([Execution requirements](execution-requirements.md)).
- Keep executions lightweight with tight timeouts and clear diagnostics when soft then hard termination occurs
  ([Execution requirements](execution-requirements.md)).

**Sources.** [Execution requirements](execution-requirements.md).

### High-volume or binary exporters
**Scenario.** Run commands that emit large logs or binary artifacts (e.g., archivers, diagnostic dumps) while
preventing hangs or memory pressure.

**Constraints.**
- Drain stdout and stderr concurrently using bounded buffers to avoid deadlocks and uncontrolled memory growth
  ([Execution requirements](execution-requirements.md);
  [Project conventions](../guidelines/icli/project-conventions.md)).
- Preserve raw byte access and avoid newline trimming so binary payloads remain intact
  ([Execution requirements](execution-requirements.md);
  [Legacy audit](../research/experiments/kotlin-solution-audit.md)).
- Provide back-pressure aware streaming pumps (prefer virtual threads) instead of busy loops, eliminating the legacy
  implementation’s readiness polling issues
  ([Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md);
  [Legacy audit](../research/experiments/kotlin-solution-audit.md)).

**Sources.**
- [Execution requirements](execution-requirements.md)
- [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)
- [Legacy audit](../research/experiments/kotlin-solution-audit.md)
- [Project conventions](../guidelines/icli/project-conventions.md)

## Interactive session use cases

### Developer REPL and shell automation
**Scenario.** Maintain long-lived sessions for `python`, `node`, `/bin/bash`, or `pwsh` to script developer workflows
and rapid feedback loops in headless environments ([Execution requirements](execution-requirements.md)).

**Constraints.**
- Expose session handles with stdin/stdout/stderr streams, optional PTY backing, and convenience helpers (`sendLine`,
  `closeStdin`, control signals) ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Allow switching between pipe-based and PTY-backed modes when prompts require a TTY; window resizing and visual layout
  fidelity are out of scope ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Detect idle or hung sessions through configurable timeouts and expose completion futures so callers can recycle
  misbehaving REPLs ([Execution requirements](execution-requirements.md)).
- Keep PTY-backed flows consistent across Linux, macOS, and Windows (ConPTY/WinPTY) with clear fallbacks when only
  pipes are available ([Execution requirements](execution-requirements.md);
  [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)).

**Sources.**
- [Execution requirements](execution-requirements.md)
- [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)
- [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)

### Expect-style prompt orchestration
**Scenario.** Automate tools that prompt for credentials or multi-step responses, combining scripted input with
verification of prompt text while staying in text-only environments
([Execution requirements](execution-requirements.md)).

**Constraints.**
- Provide expect-style helpers or integrable hooks that can watch stdout/stderr, send responses, and surface failures
  quickly ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Support PTY-backed sessions to satisfy tools that refuse to run over plain pipes (certain `sudo` or SSH flows)
  ([Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Surface control characters (Ctrl+C, Ctrl+D) and EOF semantics so scripts can terminate interactions cleanly
  ([Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Provide optional transcript logging hooks so prompt dialogues can be debugged and audited without reproducing runs
  ([Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)).

**Sources.**
- [Execution requirements](execution-requirements.md)
- [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)
- [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)

## Pooled interactive worker use cases

### Warm REPL workers
**Scenario.** Pre-warm scripting runtimes or compilers whose startup cost is high, reusing processes across multiple
client requests ([Execution requirements](execution-requirements.md); [Project roadmap](project-roadmap.md)).

**Constraints.**
- Track usage counts per worker and recycle after configurable thresholds to avoid the leaks observed in the legacy
  pool ([Execution requirements](execution-requirements.md);
  [Legacy audit](../research/experiments/kotlin-solution-audit.md)).
- Provide hooks to run warm-up commands or authentication steps when a worker is borrowed
  ([Execution requirements](execution-requirements.md)).
- Emit health metrics and remove failed workers immediately so clients do not receive poisoned sessions
  ([Execution requirements](execution-requirements.md)).

**Sources.**
- [Execution requirements](execution-requirements.md)
- [Legacy audit](../research/experiments/kotlin-solution-audit.md)
- [Project roadmap](project-roadmap.md)

### Command multiplexing with strict isolation
**Scenario.** Share pooled processes across heterogeneous commands while guaranteeing clean state between borrows
([Execution requirements](execution-requirements.md); [Legacy audit](../research/experiments/kotlin-solution-audit.md)).

**Constraints.**
- Reset working directory, environment variables, and locale between requests to deliver deterministic behaviour
  ([Execution requirements](execution-requirements.md)).
- Flush stdin/stdout buffers and support request-level timeouts independent from worker lifetime
  ([Execution requirements](execution-requirements.md)).
- Lift the legacy restriction that bound pools to a single command signature, enabling heterogenous workloads
  ([Legacy audit](../research/experiments/kotlin-solution-audit.md)).

**Sources.**
- [Execution requirements](execution-requirements.md)
- [Legacy audit](../research/experiments/kotlin-solution-audit.md)

### Long-running automation loops
**Scenario.** Alternate between computation and CLI interactions without constantly respawning processes, especially
for tooling such as morphology analyzers mentioned in roadmap notes ([Project roadmap](project-roadmap.md)).

**Constraints.**
- Enforce separate request deadlines and worker lifetime caps, escalating from soft termination to forced kill as
  needed ([Execution requirements](execution-requirements.md)).
- Capture per-request transcripts or diagnostics without leaking data across clients
  ([Execution requirements](execution-requirements.md)).
- Surface process completion futures and structured shutdown hooks so automation can gracefully drain or forcefully
  dispose workers ([Execution requirements](execution-requirements.md)).

**Sources.** [Execution requirements](execution-requirements.md); [Project roadmap](project-roadmap.md).

## Cross-cutting constraint summary
- Offer a unified command specification capturing argv, environment overrides, working directory, PTY preferences, and
  shell usage to keep APIs consistent ([Execution requirements](execution-requirements.md)).
- Require concurrent draining of stdout and stderr, using virtual threads where available, to avoid deadlocks and to
  improve resource usage
  ([Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md);
  [Project conventions](../guidelines/icli/project-conventions.md)).
- Provide explicit charset selection and preserve raw byte paths so encoding-sensitive workflows remain safe
  ([Execution requirements](execution-requirements.md);
  [Legacy audit](../research/experiments/kotlin-solution-audit.md)).
- Document signal semantics, PTY defaults, timeout ordering, and pooling policies so consumers know how the system
  behaves across platforms
  ([Execution requirements](execution-requirements.md);
  [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)).
- Ensure Kotlin-friendly APIs and testing hooks (bounded fixtures, PTY/non-PTY matrix) in line with the repository
  testing strategy ([Execution requirements](execution-requirements.md); [Testing strategy](../testing/strategy.md)).
- Support both buffered and streaming output capture with configurable caps to prevent OOM conditions while retaining
  automation-friendly summaries
  ([Execution requirements](execution-requirements.md);
  [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)).
- Implement cancellation flows that send gentle interrupts (Ctrl+C/SIGINT) before forceful termination and optionally
  clean up process trees to avoid stragglers
  ([Execution requirements](execution-requirements.md);
  [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)).

## Out-of-scope scenarios
- Full-screen TUIs (e.g., `top`, curses dashboards) that depend on terminal window sizing or cursor positioning.
- Terminal window resize APIs or TERM negotiation aimed at reproducing visual layouts.
- Graphical interfaces or colour-sensitive rendering beyond plain text required for scripted REPL and prompt flows.

## References
- [Execution requirements](execution-requirements.md)
- [Process integration KB](../knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)
- [Legacy audit](../research/experiments/kotlin-solution-audit.md)
- [Project roadmap](project-roadmap.md)
- [Project conventions](../guidelines/icli/project-conventions.md)
- [Testing strategy](../testing/strategy.md)
- [Execution engine benchmarks](../research/icli-execution-engine-benchmarks.md)
