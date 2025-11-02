# Assistant Coding Notes

This document captures project-specific style and implementation preferences surfaced during reviews so future assistant
iterations apply them automatically. Review this file before writing or refactoring code.

## Java

- Prefer `record` types for immutable value carriers (DTOs, configuration specs, options) when the fields have direct
  accessor semantics. Use canonical constructors to enforce validation or normalisation, and keep builders as sugar on
  top when callers benefit from fluent configuration.
- When refactoring existing classes, actively consider converting simple immutable POJOs (only fields + getters) into
  records, migrating validation into the canonical constructor and ensuring accessories return defensively copied data.
- Declare every production package with `@NotNullByDefault` (JetBrains annotations). Only mark explicit opt-outs with
  `@Nullable`; avoid other JetBrains annotations until the team greenlights them.

## Process documentation & TDD discipline

- Treat public APIs (interfaces, classes, records) as the single source of truth for behavioural contracts. Before
  touching code, ensure the Javadoc/specification states preconditions, postconditions, error cases, and invariants in
  enough detail to design tests without reading the implementation.
- If the documentation is insufficient, pause implementation work and update the relevant spec/documents. Only resume
  once the API contract alone allows you to derive the required tests.
- Apply strict TDD:
  1. Capture/refresh the specification.
  2. Create test classes with descriptive method names (empty bodies initially are acceptable) derived from the spec.
  3. Implement each test and run it to observe failure.
  4. Implement the minimum production code to satisfy the failing test.
  5. Repeat until the spec is fully covered; keep test logic focused exclusively on documented behaviour (no peeking at
  internal details).
- Separate responsibilities aggressively. Any non-trivial logic (launch preparation, IO pumping, timeout handling,
  buffering, etc.) should live in dedicated package-private collaborators so we can thoroughly test every scenario (
  happy path, edge cases, error conditions, state transitions). Favour many small, well-documented types over monolithic
  structures.
- Treat documentation as part of the public contract: every helper type and method (even package-private ones inside
  `core.runtime`) must carry concise Javadoc that explains what the caller can rely on
  (inputs/outputs/errors/invariants) with external users as the primary audience. Reviewers benefit too, but favour API
  clarity over implementation notes.
- Unit tests must cover happy paths, edge cases, error propagation, and state transitions. They are cheap, so prefer
  many small, focused checks over a few large ones. Design tests with the published documentation in hand: every
  documented behaviour should have coverage, and missing docs should be written before adding tests so expectations are
  explicit.
