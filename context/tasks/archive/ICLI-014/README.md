# ICLI-014 — Design worker pool architecture

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-10-28
- **Owner:** Assistant

## Overview
- **Objective:** Produce a detailed architecture specification for the worker pool layer that ProcessPool and
  WorkerLease will expose to clients.
- **Definition of Done:**
  - Document ProcessPool/WorkerLease API surface, configuration knobs, scheduling rules, eviction, and shutdown flows.
  - Capture pool lifecycle integration points with CommandService/ProcessEngine and identify required diagnostics hooks
  plus follow-up questions.
  - Update backlog/dossier to reflect progress and store the design under version control ready for maintainer review.
- **Constraints:** Align with Phase 5 roadmap scope, reuse existing ExecutionOptions defaults, keep design compatible
  with StandardProcessEngine, and respect repository documentation standards.
- **Roles to notify:** Maintainer.

## Planning
- **Scope summary:** Define the runtime and client-facing structure of pooled interactive workers, covering worker
  lifecycle, lease semantics, concurrency, and failure handling.
- **Proposed deliverables:** Process pool architecture specification (Markdown), updated roadmap references if required,
  refreshed dossier and backlog metadata.
- **Open questions / risks:** Clarify diagnostics/metrics responsibilities, determine isolation guarantees for
  heterogeneous commands, validate compatibility with async ClientScheduler helpers.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:**
  - [analysis/2025-10-28.md](analysis/2025-10-28.md)
- **Knowledge consulted:**
  - [roadmap/execution-architecture-brief.md](/context/roadmap/execution-architecture-brief.md)
  - [roadmap/execution-requirements.md](/context/roadmap/execution-requirements.md)
  - [roadmap/execution-use-case-catalogue.md](/context/roadmap/execution-use-case-catalogue.md)
  - [Java Terminal & Process Integration
  KB](/context/knowledge-base/operations/Java%20Terminal%20%26%20Process%20Integration.md)
  - [research/experiments/kotlin-solution-audit.md](/context/research/experiments/kotlin-solution-audit.md)
  - [research/icli-execution-engine-benchmarks.md](/context/research/icli-execution-engine-benchmarks.md)
- **Readiness decision:** Ready to draft the process pool specification; remaining questions will be captured as design
  follow-ups.

## Research
- **Requests filed:** None.
- **External outputs:** Refer to research documents logged in analysis entries.
- **Summary:** No external research required; prior analyses covered pooling constraints sufficiently.
- **Human response:** Not applicable.

## Execution
- **History entries:**
  - [execution-history/2025-10-28.md](execution-history/2025-10-28.md)
- **Implementation highlights:** Process pool architecture specification drafted and linked from the architecture brief.
- **Testing:** Documentation-only changes; repository checks will run before closure.
- **Follow-up work:** Address metrics/diagnostics decisions logged in the spec when implementation tasks begin.
- **Retrospective:** Tracked in [execution-history/2025-10-28.md](execution-history/2025-10-28.md).

## Completion & archive
- **Archive status:** Archived (2025-10-28).
- **Archive location:** `context/tasks/archive/ICLI-014/`.
- **Final verification:** Documentation-only diff reviewed; pre-response checks executed on 2025-10-28.

## Decisions & notes
- **Key decisions:**
  - ProcessPool advanced API will expose blocking and async acquisition, lease scope metadata, and configurable reset
  hooks described in [process-pool-architecture.md](/context/roadmap/process-pool-architecture.md).
  - Pool sizing defaults derive from available processors with reuse caps (1 000 requests) and lifetime eviction baked
  into `ProcessPoolConfig`.
  - Diagnostics integration uses a dedicated `PoolDiagnosticsListener` plus `PoolMetrics snapshot()` until broader
  metrics infrastructure lands.
- **Risks:**
  - Metrics export strategy still open; implementation needs to pick an approach or defer with clear telemetry gaps.
  - Diagnostics listener execution must remain non-blocking; implementation will need tests around backpressure.
- **Links:**
  - [context/roadmap/process-pool-architecture.md](/context/roadmap/process-pool-architecture.md)
