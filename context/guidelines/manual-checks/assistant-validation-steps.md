# Assistant Validation Steps (Draft)

## Purpose

- Serve as a single reference for manual or semi-manual validation flows that supplements automated tooling.
- Enable the maintainer to request targeted checks by pointing to named sequences in this document.
- Ensure every sequence is executed step-by-step with maintainer sign-off between steps.

## Usage

- The maintainer will ask: “Read
  [assistant-validation-steps.md](/context/guidelines/manual-checks/assistant-validation-steps.md) and execute sequence
  `name`”.
- Read the named sequence in full before starting.
- Execute the entire sequence from start to finish; do not interleave tasks from other sequences unless explicitly
  instructed.
- Collect findings as you progress; once the full sequence is complete, deliver a consolidated report that references
  each step.
- Apply all recommended code/documentation changes within the sequence before delivering the consolidated report; the
  maintainer will review the resulting diff afterward.
- Until the public API is finalised for release, assume `@NotNullByDefault` contracts apply universally—even at
  user-facing boundaries. Redundant null checks should be removed now; they will be reintroduced deliberately during the
  finalisation phase alongside any required safety guards.
- If a step cannot be completed as written, stop the sequence and consult the maintainer before proceeding.

## Structure (to be populated)

- Each sequence will live under its own heading:
    - **Sequence name** — short identifier the maintainer can cite.
    - **Intent** — why the sequence exists (e.g., style drift review, nullability audit).
    - **Preconditions** — any setup required (e.g., run a command, open specific files).
    - **Step-by-step actions** — numbered items; each intended to be executed and reported individually.
    - **Expected outputs** — what evidence or artefacts should be produced per step.
    - **Follow-up** — guidance on what to do if a step surfaces issues.

## Sequence: Nullability Guard Review

- **Intent:** Confirm that package-level `@NotNullByDefault` contracts are honoured by removing redundant null checks,
  ensuring `@Nullable` appears only when null is intentionally propagated, and preferring sentinel values over null
  where practical.
- **Preconditions:** Identify the code under review (diff, file list, or package). Verify that the relevant packages
  declare `@NotNullByDefault`.
- **Step-by-step actions:**
    1. **Scan for null guards.** Locate occurrences of `== null`, `Objects.requireNonNull`, or similar checks within the
       target scope. Record each finding with file and line.
    2. **Assess necessity.** For every guard, confirm whether upstream contracts actually permit null. If the value is
       guaranteed non-null, flag the guard as redundant; otherwise document why null is expected.
    3. **Audit annotations.** Review fields, parameters, and return values marked `@Nullable` (or effectively nullable).
       Verify that null is required; if a sentinel constant or dedicated type could replace null, note the opportunity.
    4. **Implement remediation.** Apply the necessary edits (remove redundant guards, adjust annotations, introduce
       sentinels) and ensure the code compiles. Record the changes for inclusion in the final report.
- **Expected outputs:** A consolidated report covering each step (findings, necessity analysis, annotation audit,
  applied edits) plus the resulting diff/tests.
- **Follow-up:** If the maintainer identifies gaps, address the feedback and, if required, repeat the sequence with the
  updated scope.

## Sequence: Documentation Quality Review

- **Intent:** Ensure classes and methods expose Javadoc that matches the standards of high-quality open-source libraries
  and the JDK: clear behavioural contracts, error handling, side effects, parameter/result semantics, and illustrative
  usage guidance where helpful.
- **Preconditions:** Determine the scope of documentation under review (e.g., changed files, a package, or the entire
  module). Confirm that coding standards require Javadoc for public and package-private APIs.
- **Step-by-step actions:**
    1. **Inventory API surface.** List the classes, methods, and fields within scope that require Javadoc. Note any
       missing documentation or TODO placeholders.
    2. **Evaluate behavioural coverage.** For each documented element, assess whether the Javadoc describes functional
       behaviour (happy path and failure cases), side effects, state changes, and concurrency/thread-safety
       expectations. Record gaps for improvement.
    3. **Inspect parameter and return tags.** Verify that `@param`, `@return`, and `@throws` tags exist where
       appropriate, reflect actual constraints (e.g., non-null contracts, accepted ranges), and reference specific
       exception types with explanation of when they occur. Never introduce `@throws NullPointerException`; our
       nullability policy already treats such failures as contract violations rather than documented behaviour.
    4. **Check examples and guidance.** Identify APIs that benefit from usage examples, error-handling advice, or
       cross-references (`@see`). If missing, draft concise examples or guidance that illustrate typical interactions
       and edge cases.
    5. **Implement improvements.** Add or revise Javadoc to address all identified gaps: document behaviour thoroughly,
       include parameter/result tags, mention side effects, insert examples, and ensure formatting complies with project
       standards. Update the inventory to mark each issue resolved.
- **Expected outputs:** A consolidated report summarising the initial inventory, the deficiencies found, the
  documentation changes applied (with references to specific classes/methods), and any remaining follow-ups. Provide the
  updated Javadoc in the repo diff.
- **Follow-up:** If further enhancements are requested, refine the documentation accordingly and rerun the sequence for
  the adjusted scope.

## Sequence: Test Coverage Review

- **Intent:** Validate that new or modified APIs and their documentation are matched by comprehensive unit tests
  covering all documented behaviours, including success paths, error handling, and boundary conditions.
- **Preconditions:** Collect the list of classes/methods that have been added or changed, along with their Javadoc/spec
  updates. Ensure the relevant test source sets are available.
- **Step-by-step actions:**
    1. **Map API changes.** Catalogue the methods and classes affected by the change set, noting documented
       behaviour (happy path, error cases, side effects, concurrency guarantees).
    2. **Review existing tests.** Inspect current unit tests to determine which documented behaviours are already
       covered. Highlight gaps where behaviour is undocumented in tests or missing assertions.
    3. **Design new scenarios.** For uncovered behaviours, outline the required test cases (inputs, expected outcomes,
       error handling). Include edge cases, concurrency/timeouts, and observable side effects.
    4. **Implement tests.** Add or update Kotlin/JUnit tests to cover the newly identified scenarios. Ensure tests align
       with project style (naming, assertions) and run deterministically.
    5. **Run verification.** Execute the relevant Gradle test tasks (e.g., `gradle test`, additional source sets if
       applicable) to confirm all tests pass.
- **Expected outputs:** A consolidated report summarising the API/documentation inventory, coverage analysis,
  new/updated test cases, and the verification run results, alongside the associated code changes.
- **Follow-up:** If the maintainer requests additional coverage or adjustments, refine the tests and rerun this sequence
  for the updated scope.

## Sequence: Code Smell Cleanup

- **Intent:** Improve local code clarity by eliminating common smells (e.g., long methods, deep nesting, duplicate
  logic, magic values, unnecessary mutability) and ensuring naming and structure aid readability.
- **Preconditions:** Define the scope (changed files or modules). Prepare tools for static analysis if helpful (e.g.,
  IDE inspections).
- **Step-by-step actions:**
    1. **Identify smells.** Scan the target code for standard warning signs: long/complex methods, large classes,
       duplication, excessive conditional branching, unused dependencies, magic numbers, or outdated TODO comments.
       Document each occurrence with file/line references.
    2. **Prioritise fixes.** For each finding, decide on the appropriate refactor (e.g., extract method, introduce
       constants, remove duplication, replace conditionals with polymorphism, simplify boolean logic). Treat multiple
       public classes or interfaces declared in a single source file as an automatic smell unless they form a sealed
       hierarchy whose implementations are compact and exhaustively declared in that file; even in that limited case,
       plan to split the types so the structure stays discoverable. When working with lambdas in modern Java (>=22),
       you may use the single-underscore placeholder (`_ ->`) for unused parameters—prefer that form during cleanups
       so the intent is explicit.
    3. **Refactor implementation.** Apply the selected refactors incrementally, ensuring behaviour is preserved and
       readability improves. Keep commits/diffs focused and explanatory.
    4. **Verify invariants.** Run unit tests (and other relevant checks) to confirm no regressions. If new tests are
       needed to guard against the smell returning, add them.
    5. **Document changes.** Update inline comments or TODOs where necessary and ensure the final diff clearly reflects
       the simplifications.
- **Expected outputs:** A report summarising detected smells, the refactors performed, supporting tests, and any
  remaining or deferred cleanups, accompanied by the cleaned-up code.
- **Follow-up:** Revisit the sequence if the maintainer requests further simplification or if additional smells are
  discovered later.

## Sequence: Modern API Review

- **Intent:** Ensure implementations leverage the most appropriate language and library features available on the
  project’s baseline (Java 25, Kotlin 2.2) so code stays concise, efficient, and idiomatic.
- **Preconditions:** Identify the classes or functions under review and note the language/library version constraints in
  effect. Gather release notes or migration guides for relevant JDK/Kotlin updates when uncertain.
- **Step-by-step actions:**
    1. **Catalogue target constructs.** List APIs, patterns, or idioms in the scope that look verbose, outdated, or
       reimplemented despite platform support (e.g., manual looping where `Stream`/`List` helpers exist, hand-rolled
       concurrency primitives, obsolete date/time handling).
    2. **Research modern equivalents.** For each item, confirm whether newer JDK/Kotlin APIs or library utilities offer
       a clearer or more performant alternative (records, switch expressions, scoped values, structured concurrency,
       collection factories, `Files` helpers, etc.). Record references to the official documentation or release notes
       that justify the upgrade.
    3. **Assess suitability.** Evaluate behavioural impact, readability, and performance implications. Ensure changes
       respect project constraints (e.g., no preview features, consistent nullability contracts). Flag any cases where
       adopting the modern API would break binary compatibility or substantially alter semantics.
    4. **Implement upgrades.** Refactor code to use the modern constructs, keeping diffs focused and updating related
       documentation or tests. Capture before/after snippets when the transformation is non-trivial.
    5. **Validate behaviour.** Run the relevant Gradle tasks (formatting, tests, static analysis) to verify the modern
       API usage behaves as expected and integrates cleanly.
- **Expected outputs:** A report summarising reviewed locations, chosen modernisations with references, resulting code
  changes, and verification results. Include rationale for any items left untouched (e.g., constraints preventing
  adoption).
- **Follow-up:** If reviewers uncover additional opportunities or regressions, revisit the sequence with the expanded
  scope.

## Sequence: Architecture & Invariant Review

- **Intent:** Evaluate class/module design against SOLID and GRASP principles, ensuring responsibilities and invariants
  are decomposed appropriately, dependencies respect abstraction boundaries, and the system is ready for extension
  without fragility.
- **Preconditions:** Gather architecture notes or diagrams (if available) and list the components under review.
  Understand the key invariants the code must uphold.
- **Step-by-step actions:**
    1. **Analyse responsibilities.** For each class or module, determine its primary responsibilities and check for SRP
       violations (multiple unrelated concerns). Note any signs of God objects or feature envy.
    2. **Inspect invariants.** Identify the invariants each component should maintain (state consistency, lifecycle
       rules). Verify they are enforced locally and not leaked to callers. Highlight cases where invariants are
       scattered or weakly enforced.
    3. **Assess dependencies.** Review dependency directions and coupling. Ensure high-level modules do not depend on
       low-level details (Dependency Inversion) and interfaces are appropriately segregated.
    4. **Evaluate extension points.** Consider how the design adapts to foreseeable changes. Note areas where
       modification requires touching multiple layers or where plug-in points are missing.
    5. **Refine architecture.** Implement targeted design improvements: split classes, introduce interfaces or policies,
       relocate invariants, simplify collaboration patterns, or add documentation explaining architectural decisions.
       Update or add tests that demonstrate the intended behaviour of the refined abstractions.
- **Expected outputs:** An architectural report summarising responsibilities, invariants, dependency analysis, extension
  readiness, and the applied improvements with references to updated code/tests.
- **Follow-up:** Iterate based on maintainer feedback or new requirements, potentially rerunning the sequence when
  architecture changes significantly.

## Next steps

- Populate concrete sequences in collaboration with the maintainer (e.g., “Nullability audit”, “Dependency freshness
  review”).
- Update [context/context-overview.md](/context/context-overview.md) and [AGENTS.md](/AGENTS.md) to reference this file
  once sequences are in place.
