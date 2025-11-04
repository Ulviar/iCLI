# ICLI-039 — Implement Scenario #1 — single-run command comparison

## Status
- **Lifecycle stage:** Archived
- **Overall status:** Superseded
- **Owner:** Assistant
- **Archive date:** 2025-11-04

## Summary
- Scenario #1 (single-run comparison across iCLI, Commons Exec, zt-exec, NuProcess, JLine) shipped together with task ICLI-038 when the new `samples` module gained its dual Java/Kotlin harness and coverage.
- No separate dossier was created; all planning, analysis, and execution history lives under [context/tasks/archive/ICLI-038](../ICLI-038/README.md).
- This entry simply records that ICLI-039 no longer requires independent action because its deliverables were completed as part of ICLI-038.

## Verification
- `:samples:test` exercises the fake-process harness and the real `java -version` scenario for every adapter.
- Refer to the ICLI-038 execution log for the exact Gradle commands and evidence.

## Follow-up
- None. Future scenarios will be tracked under new task IDs once scoped.
