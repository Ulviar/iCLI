# Coding Standards (Mandatory)

These rules are mandatory for all code contributed to the repository. Refer to them before writing or refactoring
code; treat violations as defects that must be corrected before submission.

## Nullability

- Every production Java package must declare `@NotNullByDefault` (JetBrains annotations).
- Parameters, return values, and fields are therefore non-null unless explicitly annotated with `@Nullable`.
- Only apply `@Nullable` when a value is intentionally allowed to be `null`; avoid using it for convenience defaults.
- Do not duplicate null checks (`Objects.requireNonNull`, manual `null` guards) for values covered by the default
  contract.
- Builders may provide optional arguments by accepting `@Nullable` parameters and substituting documented defaults; all
  other parameters must be required and non-null.
- Rationale: redundant runtime guards weaken the declared contract, mask erroneous call sites, and create churn when
  API signatures evolve. Treat `@NotNullByDefault` as the single source of truth and rely on tests or static analysis to
  expose violations instead of defensive checks.

## Immutability

- Prefer Java `record` types for immutable value carriers. Use canonical constructors for validation and normalisation.
- Perform defensive copies for incoming mutable collections or arrays inside the record constructor or builder.

## Collections and Defensive Copies

- Treat the canonical constructor of a record as the single enforcement point for immutability. Builders should pass
  their working collections directly to the record and let the constructor perform the defensive copy once.
- Prefer the JDK `copyOf`/factory helpers (`List.copyOf`, `Set.copyOf`, `Map.copyOf`, `List.of`, etc.) over manual
  `new ArrayList<>(...)` or `clone()` calls. They already snapshot inputs, reject `null` elements, and optimise empty
  collections, so extra branches such as `isEmpty() ? List.of() : List.copyOf(...)` only add noise.
- Vararg builders can rely on `List.of(values...)` to clone the provided array. Avoid `argv.clone()`—the additional copy
  is redundant and obscures intent.
- Choose collection implementations deliberately. Use `LinkedHashMap` when deterministic iteration order is part of the
  API contract (e.g., environment variables for diagnostics); otherwise prefer the simplest immutable view returned by
  the `copyOf` helpers.
- Rationale: keeping defensive logic centralised and using the JDK’s immutable factories prevents double copying,
  reduces accidental mutability, and keeps the intent obvious to reviewers. Reintroducing manual copies or redundant
  branches makes the code harder to reason about and encourages future inconsistencies.

## Formatting

- Format Java with Palantir Java Format via Spotless (configured for version 2.80.0); Kotlin uses ktlint.
- Do not introduce custom formatting or manual line wrapping; rely on Spotless for consistency.

## Testing and Process

- Follow test-driven development: write or adjust a failing test before implementing behaviour.
- All new code must be covered by automated tests and verified with `./gradlew test` locally.
- Capture design rationales or extended answers in `EXPLANATION.md` when requested.
- Prefer importing assertion helpers (e.g., `import kotlin.test.fail`) over qualifying them with package names to keep test code concise and readable.

## Documentation

- Document every class and interface with Javadoc that states its responsibility and observable behaviour, even when it
  is package-private and only referenced internally. Avoid mentioning repository documents or implementation trivia.
- Provide method-level Javadoc for all public and package-private methods (helpers included). Describe inputs,
  outputs, error cases, and invariants so tests can be derived directly from the text.
- Aim the documentation at external/library users first, while still providing enough clarity for reviewers and future
  maintainers. If implementation context is necessary, prefer clearer code or targeted inline comments over bloating
  the Javadoc.
- When suppressing static-analysis warnings, include a justification in the annotation explaining why the flagged
  behaviour is intentional.

## Parameter validation

- For code that remains entirely inside the iCLI runtime (e.g., collaborators under `core.runtime`), treat the caller as
  responsible for passing valid arguments; avoid redundant guards on internal call paths so hot code stays lean.
- Validate inputs that originate from external callers (public API entry points, user-supplied data, configuration).
- Null-handling follows the repository-wide annotation policy: honour `@NotNullByDefault`, and add explicit checks only
  when dealing with data sourced outside the annotated packages.

Keep `context/guidelines/icli/assistant-notes.md` updated with project-specific tips derived from these standards.
