# iCLI Project Conventions

These notes extend the generic contributor guidelines with decisions that are unique to the iCLI command execution
library.

## Source layout

- Production packages live under `com.github.ulviar`. Tests mirror the package tree from `src/test/kotlin`.
- Place interactive or benchmarking samples in `samples/` and add a scoped [AGENTS.md](/AGENTS.md) when extra
  instructions are required.
- Keep shared test fixtures alongside the tests that own them unless they are reused across packages.

## Design principles

- Apply SOLID and GRASP heuristics aggressively. Keep collaborators small, cohesive, and replaceable so assistants can
  reason about them in isolation while maintaining extensibility for future open-source contributions.
- Convert immutable data holders into Java records where practical; enforce invariants in canonical constructors and
  expose defensive copies for mutable inputs.
- Separate orchestration from IO- or process-heavy work. Favour package-private helpers that encapsulate logic which can
  be unit-tested without launching processes.
- Document module boundaries and public API contracts before implementation. If expectations are unclear, update the
  roadmap or knowledge base prior to coding so tests can follow TDD.

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

## Development workflow

- Format Java sources with **Palantir Java Format** through Spotless; Kotlin continues to follow Kotlin conventions.
- Practice TDD: write or update tests first, then implement changes to make them pass, keeping the build green at all
  times.
- Run the relevant Gradle compile/test tasks before committing or sharing changes to guarantee the code compiles.
- TODO markers are welcome during design; annotate them with context/owner and create backlog items if the follow-up
  will not be addressed immediately.
- Consult [assistant-notes.md](/context/guidelines/icli/assistant-notes.md) for project-specific patterns expected from
  AI contributions (e.g., defaulting to Java records for immutable configuration objects).
- When a longer design explanation is required, capture it in [EXPLANATION.md](/EXPLANATION.md) (written in the current
  conversation language). Document motivation, key decisions, alternatives, and the planned tests there on demand.
- Apply JetBrains annotations consistently: each Java package declares `@NotNullByDefault`, opt-out points use
  `@Nullable`, and other nullability annotations remain off-limits unless agreed otherwise.

## Documentation hooks

- Capture novel findings about process execution in the knowledge base under `context/knowledge-base/operations/`.
- Link new experiments through [context/research/registry.md](/context/research/registry.md) so future work can trace
  prior analysis.
- Keep release-specific steps in [context/workflow/releases.md](/context/workflow/releases.md) once publication
  processes are formalized.
