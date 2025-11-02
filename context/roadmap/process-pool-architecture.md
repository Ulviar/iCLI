# Process Pool Architecture Specification

## Purpose
- Define the architecture for reusable interactive workers that back ProcessPool, WorkerLease, and the Essential
  ServiceProcessor facade promised in the execution roadmap.
- Provide implementation guidance for ICLI-015/ICLI-016 so the runtime, client APIs, and diagnostics evolve coherently.

## Goals
- Deliver a precise API surface for `ProcessPool` (advanced API) and `WorkerLease`, including threading, lifecycle, and
  error-handling contracts.
- Describe configuration knobs (pool sizing, reuse caps, eviction, reset hooks, per-request deadlines) that align with
  `ExecutionOptions` defaults and Phase 5 roadmap expectations.
- Specify scheduling, fairness, and backpressure semantics for lease acquisition and queued requests.
- Capture shutdown behaviour, failure recovery, and diagnostics events so future tasks can instrument pool health.
- Map capabilities directly to the catalogue’s pooled scenarios (warm REPL workers, command multiplexing with isolation,
  long-running automation loops, stateful conversations) so backlog items can target them explicitly
  ([execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md)).

## Non-goals
- Implement the pool runtime (ICLI-015 will translate this spec into code).
- Define Essential API details beyond the pool boundary (ICLI-016 owns `ProcessPoolClient` and `ServiceProcessor`
  specifics, though this document outlines the integration points the pool must expose).
- Replace StandardProcessEngine internals; the design assumes reuse of the current launch, shutdown, and diagnostics
  infrastructure.

## Terminology
- **Worker** — A long-lived interactive session obtained via `ProcessEngine.startSession`, maintained by the pool.
- **Lease** — A scoped handle that grants exclusive access to a worker for a single request cycle.
- **Pool** — The coordinator that manages worker creation, queuing, and eviction.
- **Reset hook** — Logic executed after each lease to restore worker state (cwd, env, transient files).
- **Warmup action** — Optional commands run immediately after worker launch before the first lease is granted.

## API surface

### Core interfaces (advanced API)
- `ProcessPool` (package `com.github.ulviar.icli.engine.pool`):
  - `WorkerLease acquire(Duration timeout)` — block until a worker is available or timeout elapses; throws
  `ServiceUnavailableException` on timeout or pool shutdown.
  - `CompletionStage<WorkerLease> acquireAsync(ClientScheduler scheduler, Duration timeout)` — optional helper for async
  integration; implemented by delegating to `ClientScheduler.submit`.
  - `void close()` — initiate graceful shutdown (no new leases, wait for active leases, then retire workers).
  - `boolean drain(Duration timeout)` — cooperative drain that waits for active leases to finish and retires workers;
  returns false when the timeout expires before completion.
  - `PoolMetrics snapshot()` — immutable view of utilisation counters (workers active/idle, queue depth, failures).
- `WorkerLease` (package `com.github.ulviar.icli.engine.pool`):
  - `InteractiveSession session()` — expose the underlying session for custom dialogues.
  - `ExecutionOptions executionOptions()` — per-worker options used for request-level deadlines.
  - `LeaseScope scope()` — context object providing helpers and metadata (request id, worker id, diagnostics context).
  - `void reset(ResetRequest request)` — run registered reset hooks immediately; invoked automatically on `close()` but
  callable by advanced clients who need mid-lease reset.
  - `void close()` — mandatory release that returns the worker to the pool, executing reset hooks and evaluating reuse
  limits; idempotent.
- Exceptions:
  - `ServiceUnavailableException` — pool exhausted, timed out, or shutting down.
  - `ServiceProcessingException` — surfaced when worker execution fails (mirrors architecture brief).

### Configuration types
- `ProcessPoolConfig`:
  - `CommandDefinition workerCommand` — required; describes the session to keep warm.
  - `ExecutionOptions workerOptions` — defaults derived from `ExecutionOptions.builder()` but with idle timeout disabled
  (pool manages its own idleness); callers may override.
  - `int minSize` / `int maxSize` — pool sizing bounds (default: min 0, max `min(max(availableProcessors / 2, 1), 8)`).
  - `int maxQueueDepth` — optional bounded queue for waiting leases (default: unbounded).
  - `int maxRequestsPerWorker` — reuse cap (default 1 000).
  - `Duration maxWorkerLifetime` — absolute time cap per worker (default 30 minutes; negative disables).
  - `Duration maxIdleTime` — idle eviction threshold (default 5 minutes).
  - `Duration leaseTimeout` — default timeout for `acquire` when caller does not specify one (default 30 seconds).
  - `boolean destroyProcessTree` — defaults to `ExecutionOptions.destroyProcessTree()`.
  - `Optional<WarmupAction>` — invoked immediately after worker launch.
  - `List<ResetHook>` — executed after each lease; built-in hooks include cwd reset, env reset, and flushing
  stdin/stderr.
  - `PoolDiagnosticsListener` — optional observer for lifecycle events; defaults to no-op.
  - `Clock clock` / `ScheduledExecutor` overrides for deterministic tests (package-private constructors).
- `LeaseScope`:
  - `UUID requestId`, `int workerId`, `Instant leaseStart`, `PoolDiagnostics` context.
  - Utility helpers like `CompletableFuture<Integer> onExit()` (delegates to session) and `void sendSignal(...)`.
- `WarmupAction` / `ResetHook`:
  - Functional interfaces receiving `InteractiveSession` and `LeaseScope`.
  - Executed on virtual threads spawned via `Thread.startVirtualThread`, with timeout enforcement from config.

### Essential API projections
- `ProcessPoolClient` will wrap `ProcessPool` and expose high-level request helpers, but the pool must provide:
  - `LeaseExecutor` helper to run a callback while managing reset/exception translation.
  - `RequestResult` describing per-request completion for `ServiceProcessor`.
  - Hook to attach request-level diagnostics metadata (request id, truncated output counters).

## Lifecycle overview

### Pool states
- **Starting** — Pre-populates `minSize` workers asynchronously.
- **Running** — Accepts `acquire` calls, launching new workers on demand up to `maxSize`.
- **Draining** — Triggered by `close()`; rejects new acquisitions, waits for existing leases to return, then retires
  workers via `ShutdownExecutor`.
- **Terminated** — All workers disposed; further operations throw `IllegalStateException`.

### Worker states
- **Idle** — Ready for leasing; on a FIFO idle queue.
- **Leased** — Assigned to a client; tracked with lease metadata.
- **Resetting** — Reset hooks running before returning to idle queue.
- **Retiring** — Marked for shutdown due to reuse cap, lifetime, idle eviction, or failure; removed from queues.

### Sequence outlines
1. **Acquisition**: caller invokes `acquire(timeout)` → queue enqueues waiter → when idle worker available (or a new
   worker created) → waiter completes with `WorkerLease`.
2. **Release**: caller closes lease → reset hooks execute (virtual thread) → pool checks reuse/lifetime thresholds →
   either re-queues worker or transitions to retiring and schedules shutdown.
3. **Failure**: worker dies during lease → lease close surfaces `ServiceProcessingException` → pool marks worker
   unhealthy, schedules replacement if below `minSize`.
4. **Shutdown**: `close()` moves pool to draining → waits for active leases (bounded by `drain` timeout when provided) →
   retires workers via existing `ShutdownExecutor`.

## Configuration model

1. **Worker command** — The pool owns the command definition and must not permit per-request mutation that would change
   binary/argv. Advanced callers can still run heterogeneous commands by sending scripts through stdin (document the
   state risks explicitly in Essential APIs).
2. **Execution options** — Start from repo defaults, disable idle timeout (pool applies `maxIdleTime`), honour shutdown
   plan and diagnostics listener overrides.
3. **Sizing and scaling** — Implement eager creation up to `minSize`, lazy expansion to `maxSize` when queue waiters
   exceed idle workers, and shrink by eviction when utilisation stays low for `maxIdleTime`.
4. **Lease deadlines** — Each lease obtains a deadline derived from `leaseTimeout` unless caller supplies an explicit
   timeout; request-level cancellation interrupts the worker via `ShutdownPlan` soft signal then hard kill.
5. **Reset policy** — Provide default hooks:
   - Flush stdin (send Ctrl+U?)? Instead flush internal writers and ensure output drains to avoid leftover data.
   - Reset working directory to configured base.
   - Restore environment overrides (drop temp variables introduced by clients by reapplying baseline map).
   - Optionally execute `ResetHook` actions; treat failures as worker faults leading to retirement.
6. **Warmup** — `WarmupAction` runs once after launch; failure retires worker immediately and retries launch subject to
   exponential backoff (start at 250 ms, double up to 2 s) to avoid spin.
7. **Diagnostics** — `PoolDiagnosticsListener` receives events for worker creation, warmup start/end, lease acquire
   success/failure, reset outcome, eviction, shutdown progress. Events include pool/worker/request identifiers and
   timings. Listener runs on virtual threads to avoid blocking pool coordination.
8. **Backpressure** — When queue exceeds `maxQueueDepth`, new acquisition attempts fail fast with `ServiceUnavailable`.

## Lease flow

### Acquisition
- Guarded by a single `ReentrantLock` protecting worker queues and waiter list.
- Waiters stored as FIFO deque of `CompletableFuture<WorkerLease>`.
- When a worker becomes idle, the pool completes the head waiter; if no waiters, worker joins idle deque.
- Timeout handling: schedule a timer per waiter using `ScheduledExecutor` (virtual-thread friendly) that completes the
  future exceptionally when deadline passes and removes waiter from deque.

### Execution
- Advanced clients interact with `InteractiveSession` directly; Essential APIs will provide helpers that stream input,
  await completion, and convert to `ClientResult`.
- Each lease receives a `LeaseScope` capturing start timestamp, request id, and default deadline for instrumentation.
- Request-level deadlines enforced via `IdleTimeoutScheduler` or a new `LeaseDeadline` helper: when exceeded, pool
  triggers `ShutdownPlan` for the worker and marks lease failed.

### Completion
- `close()` triggers:
  1. Mark lease finished, inform diagnostics.
  2. Run reset hooks sequentially on a virtual thread (failures treated as worker faults).
  3. Increment reuse count and update last-used timestamp.
  4. If thresholds exceeded or worker flagged unhealthy, transition to retiring; otherwise reinsert into idle deque.

### Failure categories
- **Launch failure** — Occurs during worker creation or warmup; pool increments failure counter, schedules retry up to
  configured backoff, surfaces `ServiceUnavailable` if no workers available.
- **Request failure** — Command exits non-zero or times out; Essential API translates to `ServiceProcessingException`
  while worker reset policy decides whether to recycle (default: retire on timeout or exit caused by hard kill).
- **Reset failure** — Reset hook throws; worker immediately retires and error propagated to caller.
- **Worker crash** — Session future completes unexpectedly; pool marks worker dead, notifies diagnostics, optionally
  replays queued waiters by launching replacement.

### Recycling and eviction
- Worker retires when:
  - reuse count >= `maxRequestsPerWorker`;
  - lifetime >= `maxWorkerLifetime`;
  - idle duration >= `maxIdleTime`;
  - reset hook flagged `RETIRE`.
- Retiring uses `ShutdownExecutor` with the worker’s shutdown plan. Pool ensures replacement launched if utilisation
  demands (maintain `minSize` and honour waiters).

## Shutdown semantics
- `close()` transitions to draining immediately; new acquisition attempts fail with `ServiceUnavailable`.
- Pool waits for active leases up to `drain` timeout; during drain, queue waiters complete exceptionally.
- After leases finish, pool applies shutdown plan to each worker sequentially (launching force kill if necessary).
- `drain(Duration)` helper returns true when all workers retired within timeout, enabling graceful shutdown reporting.
- Forceful shutdown path (when close interrupts) kills all workers with `ShutdownPlan` hard signal and clears queues.

## Diagnostics and metrics
- Emit structured events via `PoolDiagnosticsListener`:
  - `workerCreated`, `workerWarmupStarted/Completed`, `workerRetired`, `workerFailed`.
  - `leaseQueued`, `leaseAcquired`, `leaseReleased`, `leaseTimeout`, `leaseResetFailed`.
  - `poolDraining`, `poolTerminated`.
- Include monotonic counters and durations inside `PoolMetrics`: active workers, idle workers, pending waiters, total
  leases served, failed launches, active resets.
- Provide hook to attach a `DiagnosticsListener` per worker so streaming output can be tagged with worker id/request id.
- AMA: metrics storage not built yet; expose `PoolMetrics snapshot()` for pull-based reporting and leave integration
  with external metrics (Micrometer, etc.) to future work.

## Integration points
- **ProcessEngine** — Pool always calls `ProcessEngine.startSession`; `ProcessPoolConfig` supplies command/options.
- **ExecutionOptions** — Reuse defaults, but override idle timeout to `Duration.ZERO` so pool owns idle detection.
- **IdleTimeoutScheduler** — Pool installs its own lease deadline enforcement using the existing scheduler for
  consistency.
- **ClientScheduler & async APIs** — `acquireAsync` uses `ClientScheduler.submit`; Essential APIs layer their futures on
  top without duplicating concurrency controls.
- **ProcessPoolClient/ServiceProcessor** — Pool exposes `LeaseExecutor` (function returning `CommandResult`) so higher
  layers can implement `process`, `processAsync`, and Kotlin suspending variants with consistent error translation.
- **Diagnostics bus** — Pool events feed into future diagnostics infrastructure; until the full bus exists, listeners
  are synchronous callbacks similar to current `DiagnosticsListener`.

## Testing strategy
- Unit tests for coordination logic using fake workers (Mock `InteractiveSession`) to verify queue ordering, timeouts,
  and eviction.
- Integration tests launching lightweight commands (e.g., `cat`) to validate reset hooks, reuse caps, and shutdown.
- Stress scenarios covering rapid acquire/release, worker crash mid-request, and idle eviction to ensure no leaks.
- Deterministic tests use fake `Clock` and `ScheduledExecutor` injected via package-private constructors.

## Open questions & follow-ups
- Should the diagnostics listener surface per-request transcript identifiers or defer to a future tracing facility?
- Determine whether to expose structured metrics via a dedicated SPI or continue relying on `PoolMetrics snapshot()`.
- Explore optional observability hook for queue depth thresholds (e.g., emit warning when waiters exceed a limit).
- Validate whether default reset hooks need to flush partial stdout/stderr left in pipes or if existing pumps suffice.
- Investigate providing built-in warmers for common shells to demonstrate heterogenous command safety patterns.
