# Follow-up Plan

1. Extract specialised collaborators from `ProcessPool` for launch/prewarm, timeout supervision, and metrics reporting
   to reduce the current God-object pressure.
2. Introduce a runtime-level scheduler abstraction so `ProcessPool` and other engine internals no longer depend on
   client-facing `ClientScheduler`.
3. Review pooled client shutdown flows after the refactor to ensure diagnostics, draining, and reset hooks remain
   consistent.
