# iCLI Project Conventions

These notes extend the generic contributor guidelines with decisions that are unique to the iCLI command execution
library.

## Source layout

- Production packages live under `com.github.ulviar`. Tests mirror the package tree from `src/test/kotlin`.
- Place interactive or benchmarking samples in `samples/` and add a scoped `AGENTS.md` when extra instructions are
  required.
- Keep shared test fixtures alongside the tests that own them unless they are reused across packages.

## Process handling

- Drain `stdout` and `stderr` concurrently for every launched process. Avoid shell wrappers unless they provide
  essential semantics.
- Prefer PTY/ConPTY attachments for interactive commands and document platform-specific quirks inside the knowledge
  base.
- Use explicit UTF-8 encoding when converting text streams; document deviations when commands require other charsets.

## Concurrency and resource notes

- Virtual threads are the default for background pumps. Do not create dedicated executors unless coordination requires
  it; prefer `Thread.startVirtualThread` directly.
- Re-interrupt threads after catching `InterruptedException` and document any non-trivial lifetime management decisions
  with Javadoc or KDoc.

## Documentation hooks

- Capture novel findings about process execution in the knowledge base under `context/knowledge-base/operations/`.
- Link new experiments through `context/research/registry.md` so future work can trace prior analysis.
- Keep release-specific steps in `context/workflow/releases.md` once publication processes are formalized.
