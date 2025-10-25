# ICLI-006 — Decide PTY dependency for interactive support

## Status
- **Lifecycle stage:** Done
- **Overall status:** Done
- **Last updated:** 2025-10-25
- **Owner:** Assistant

## Overview
- **Objective:** Select the PTY integration dependency that iCLI will adopt for interactive session support and document
  the rationale.
- **Definition of Done:** Evaluation covers pty4j, Apache Commons Exec, JNA ConPTY, and Jansi; findings address
  maintenance health, licensing, JNI/native footprint, and Windows ConPTY coverage; dependency choice is recorded with
  justification; roadmap and dossier updated to reflect the decision.
- **Constraints:** Follow repository research workflow, rely on authoritative sources for release/licensing data, and
  keep documentation in English with Markdown guidelines.
- **Roles to notify:** Maintainer (architecture/technology decisions).

## Planning
- **Scope summary:** Perform comparative analysis of PTY-capable libraries and capture a decision record for the
  execution engine roadmap.
- **Proposed deliverables:** Updated task dossier (planning, analysis, execution notes), refreshed roadmap decision
  entry, `.commit-message` summary.
- **Open questions / risks:** Monitor JetBrains’ WinPTY → ConPTY roadmap and plan if/when to pilot JLine’s prompt module
  for richer CLI ergonomics (primary PTY dependency remains pty4j with EPL-1.0 approved for use).
- **Backlog link:** [context/tasks/backlog.md](../../backlog.md)

## Analysis
- **Log entries:** [analysis/2025-10-24.md](analysis/2025-10-24.md), [analysis/2025-10-25.md](analysis/2025-10-25.md)
- **Knowledge consulted:** See latest analysis log for external sources and internal knowledge base references.
- **Readiness decision:** Execution complete; dossier ready for archival after bookkeeping updates.

## Research
- **Requests filed:** None.
- **External outputs:** None.
- **Summary:** Pending.
- **Human response:** N/A.

## Execution
- **History entries:** [execution-history/2025-10-24.md](execution-history/2025-10-24.md),
  [execution-history/2025-10-25.md](execution-history/2025-10-25.md)
- **Implementation highlights:** Dossier updated with comparative matrix, roadmap Phase 2 PTY dependency item marked
  complete with pty4j decision, and supplemental JLine evaluation captured.
- **Testing:** Not applicable (documentation-only task).
- **Follow-up work:** Monitor WinPTY → ConPTY roadmap progress and schedule a future spike if JLine’s prompt module
  proves necessary for higher-level interactive client APIs.
- **Retrospective:** Logged in the execution history entries.

## Completion & archive
- **Archive status:** Archived on 2025-10-25
- **Archive location:** `context/tasks/archive/ICLI-006/README.md`
- **Final verification:** Decision reviewed internally; no automated checks required.

## Decisions & notes
- **Evaluation summary:**

  | Option | Maintenance snapshot | License | Native footprint | Windows PTY strategy | Outcome |
  | --- | --- | --- | --- | --- | --- |
  | pty4j | 0.13.11 released 2025-09-22 (active cadence). ([FreshPorts](https://www.freshports.org/devel/pty4j/)) | [EPL-1.0](https://github.com/JetBrains/pty4j/blob/master/LICENSE.txt) | Ships JNI + native binaries (forkpty, WinPTY). | Uses WinPTY backend per [README](https://github.com/JetBrains/pty4j/blob/master/README.md#project). | Recommended base dependency; monitor EPL obligations and WinPTY migration gap. |
  | Apache Commons Exec | Latest 1.4.0 (2017-03-05) with minimal changes since. ([Release notes](https://commons.apache.org/proper/commons-exec/changes-report.html#1.4.0)) | Apache-2.0 | Pure Java (no JNI). | No PTY/ConPTY features; API centres on Process/streams per [user guide](https://commons.apache.org/proper/commons-exec/apidocs/org/apache/commons/exec/package-summary.html). | Not suitable (lacks PTY coverage). |
  | Direct JNA bindings | Core library 5.18.1 published 2025-09-10. ([GitHub](https://github.com/java-native-access/jna)) | Dual Apache-2.0 / LGPL-2.1 | Requires custom native interop descriptors. | Would need bespoke ConPTY (`CreatePseudoConsole`) implementation plus Unix PTY ffi (see [Microsoft ConPTY brief](https://devblogs.microsoft.com/commandline/windows-command-line-introducing-the-windows-pseudo-console-conpty/)). | High engineering cost; defer unless pty4j gaps prove blocking. |
  | Jansi | 2.4.2 release (2024-02-04) marking project unmaintained. ([Release note](https://github.com/fusesource/jansi/releases/tag/jansi-2.4.2)) | Apache-2.0 | JNI wrappers for ANSI emulation. | Focused on ANSI color translation, no PTY. | Rejected (project sunset, scope now subsumed by JLine modules). |
  | JLine 3 | Active cadence (e.g., [3.30.3 release on 2025-05-22](https://github.com/jline/jline3/releases)) with roadmap toward 4.x modular providers. | [BSD-3-Clause](https://docs.oracle.com/en/database/oracle/oracle-database/23/dblic/Open-Source-Software-License-Text.html#GUID-28FB32C2-CFBE-46C4-BF64-5A7096A877AF) | Core APIs are pure Java; terminal providers plug in JNA/Jansi/JNI/FFM modules as needed. ([Terminal docs](https://jline.org/docs/terminal), [Provider matrix](https://jline.org/docs/modules/terminal-providers/), [JPMS guidance](https://jline.org/docs/modules/jpms/)) | Provides terminal abstraction and signals, can pair with Pty4j for PTY and needs JNA/Jansi on Windows unless JNI/FFM is configured. ([Terminal docs](https://jline.org/docs/terminal)) | Complementary layer for interactive UX; not a PTY spawner, but its prompt module can help evaluate higher-level CLI flows when paired with pty4j. |

- **Key decisions:** Adopt pty4j as the initial PTY provider with EPL-1.0 use approved by the maintainer, keep JLine out
  of the baseline dependency graph for now, and reserve its prompt module for optional experiments on interactive
  clients.
- **Risks:** WinPTY backend might limit certain Windows behaviours until ConPTY support is available; monitor JetBrains’
  roadmap and revisit if blockers emerge.
- **Links:** [context/roadmap/project-roadmap.md](../../../roadmap/project-roadmap.md)
