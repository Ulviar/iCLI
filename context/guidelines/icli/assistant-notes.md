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
