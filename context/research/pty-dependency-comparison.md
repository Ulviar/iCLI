# PTY Dependency Comparison (2025-10-26)

## Purpose
Document the PTY-capable process libraries evaluated for iCLI so future tasks can reuse the findings instead of
repeating manual research. The study was completed during task ICLI-006 and revisited on 2025-10-26 when the project
requested a consolidated research artifact.

## Evaluation criteria
- **Maintenance cadence** — recent releases and evidence the project is still supported.
- **License compatibility** — ability to coexist with iCLI’s Apache-2.0 license without additional obligations.
- **Native/TTY coverage** — quality of PTY/ConPTY support plus any required JNI/JNA components.
- **Windows strategy** — explicit support for WinPTY or ConPTY since Windows prompts behave differently from Unix.
- **Scope fit** — whether the library provides a child-process PTY backend (required) or focuses on higher-level
  terminal UX only.

## Option summary

| Option | Maintenance snapshot | License | Native footprint | Windows PTY approach | Notes |
| --- | --- | --- | --- | --- | --- |
| **pty4j** | 0.13.11 released 2025-09-22; JetBrains actively maintains it. | EPL-1.0 | Ships JNI wrappers plus native binaries (forkpty on Unix, WinPTY on Windows). | Uses WinPTY backend today; ConPTY migration is on JetBrains’ roadmap but not shipped yet. | Only JVM library in the comparison that already provides a drop-in PTY/ConPTY-style API and a stable Java façade. Selected as iCLI’s baseline dependency with maintainer-approved EPL usage. |
| **Apache Commons Exec** | Last major release 1.4.0 on 2017-03-05 with minimal follow-up activity. | Apache-2.0 | Pure Java (no native bits). | None — library only manages pipes/watchdogs. | Reliable process launcher for non-interactive work but lacks any PTY features, so it cannot satisfy interactive session requirements. |
| **Direct JNA bindings** | Core JNA 5.18.1 published 2025-09-10; community-maintained. | Dual Apache-2.0 / LGPL-2.1 | Would require custom descriptors plus native headers for forkpty/ConPTY. | ConPTY must be implemented manually via `CreatePseudoConsole`; Unix PTY would also need bespoke bindings. | Provides full control but demands a sizeable engineering effort and ongoing maintenance of platform-specific code. Deferred until pty4j proves insufficient. |
| **Jansi** | 2.4.2 (2024-02-04) marked the project as unmaintained. | Apache-2.0 | JNI wrappers primarily for ANSI color translation. | No PTY functionality. | Focused on terminal colouring rather than process control; superseded by JLine modules. Rejected. |
| **JLine 3** | Active cadence (e.g., 3.30.3 on 2025-05-22). | BSD-3-Clause | Core modules are pure Java; terminal providers optionally pull JNA/JNI/Jansi/FFM. | Provides terminal abstractions/signals but still requires an external PTY backend such as pty4j. | Valuable as an optional front-end (prompt handling, signal routing) layered on top of a PTY provider; not a replacement for pty4j. |

## Decision
- Adopt **pty4j** as the default PTY provider for Phase 4 interactive work. EPL-1.0 compatibility was reviewed with the
  maintainer on 2025-10-25 and accepted for dependency use.
- Keep **Apache Commons Exec** and **JNA** in reserve for pipe-only workflows or bespoke PTY experiments, but do not add
  them to the dependency graph today.
- Treat **Jansi** as deprecated. Prefer **JLine 3** only when higher-level prompt UX is needed, and even then keep it an
  optional layer above pty4j to avoid dragging terminal abstractions into the core runtime prematurely.

## Follow-up recommendations
1. Track JetBrains’ progress on migrating pty4j’s Windows backend from WinPTY to ConPTY. Once ConPTY support lands,
   schedule regression tests on Windows to validate Ctrl+C handling and Unicode IO under the new backend.
2. When interactive APIs are implemented (ICLI-009), document the PTY provider hot-swap points so alternative backends
   can be evaluated without large refactors.
3. If richer prompt experiences become a priority, prototype JLine 3’s prompt/terminal modules in a separate client
   package to keep the core runtime lean.
