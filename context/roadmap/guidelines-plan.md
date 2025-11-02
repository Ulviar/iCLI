# Guidelines, Standards, and Conventions — Planning Brief

## Objective
Define a cohesive, assistant-friendly set of project-wide guidelines, coding standards, and conventions that align the
Java 25 production codebase and the Kotlin 2.2.20/JUnit 6 test stack with the repository's roadmap goals, quality gates,
and tooling choices.

## Desired Coverage
- **Source structure and ownership** — directory layout, module boundaries, and responsibilities for Java/Kotlin code,
  tests, and supporting assets.
- **Coding standards alignment** — how Palantir Java Format, Spotless, and official Kotlin conventions translate into
  day-to-day rules (naming, nullability, concurrency patterns, annotations).
- **Build, tooling, and automation** — required Gradle tasks, Spotless/SpotBugs integration expectations, and default CI
  checks.
- **Testing expectations** — unit vs. integration criteria, fixtures, PTY/process simulators, timeouts, and coverage
  thresholds.
- **Documentation practices** — [project-overview.md](/context/guidelines/project-overview.md) maintenance, API docs,
  changelog cadence, and knowledge base update triggers oriented around assistant context needs.
- **Collaboration workflow** — branching, code review expectations, definition of done, and release checklists.

## Work Plan
1. Audit existing repository artifacts (project overview, build scripts, knowledge base) to identify implicit standards.
2. Capture maintainer requirements for formatting, testing, CI, and release governance with an emphasis on SOLID/GRASP
   modularity for AI assistants.
3. Draft structured guideline sections per coverage area, cross-linking to automated enforcement (Spotless, SpotBugs,
   Gradle tasks) and ensuring every path is discoverable from the project overview.
4. Review the consolidated guidance with the maintainer and update roadmap/documentation for any new tooling
   dependencies or outstanding backlog items.
5. Finalize guidelines and publish them alongside an adoption/rollout checklist that tracks assistant readiness.

## Completion Criteria
- Each coverage area yields a documented section with actionable rules and references to supporting tools or scripts.
- Guidelines map to enforceable checks (formatting, static analysis, tests) or include a plan to add them.
- Documentation integrates with existing knowledge base and roadmap entries without duplication.
- Maintainer review sign-off recorded, and follow-up tasks (if any) captured in the task tracker.
- Repository automation (e.g., Gradle tasks, CI config) reflects any new requirements before closing the effort.

## Status snapshot — 2025-11-02

| Coverage area | Authoritative reference | Status | Notes |
| --- | --- | --- | --- |
| Source structure & ownership | [project-overview.md](/context/guidelines/project-overview.md) → [contributor-guidelines.md](/context/guidelines/general/contributor-guidelines.md), [project-conventions.md](/context/guidelines/icli/project-conventions.md) | ✅ Documented | Reinforces assistant-first modularity guidance. |
| Coding standards alignment | [coding/standards.md](/context/guidelines/coding/standards.md) | ✅ Documented | Palantir Java Format, Spotless, Kotlin conventions current as of 2025-11-02. |
| Build, tooling, automation | [project-overview.md](/context/guidelines/project-overview.md) → `Build, tooling, and automation`; [quality-gates.md](/context/workflow/quality-gates.md) | ✅ Documented | Quality gates and mandatory scripts published; keep in sync with new automation. |
| Testing expectations | [testing/strategy.md](/context/testing/strategy.md) | ✅ Documented | PTY matrix, fixture guidance, and TDD workflow documented; add concrete fixture entries as they appear. |
| Documentation practices | [project-overview.md](/context/guidelines/project-overview.md) → `Documentation practices` section | ✅ Documented | Continue linking new knowledge base entries as they appear. |
| Collaboration workflow | [workflow/collaboration.md](/context/workflow/collaboration.md),
[workflow/releases.md](/context/workflow/releases.md), [quality-gates.md](/context/workflow/quality-gates.md) | ✅
Documented | Collaboration guide now references automation checkpoints and session close-out steps.

### Next steps
- Deliver the expanded testing strategy, workflow quality-gates guide, and automation notes referenced above.
- Keep the status snapshot aligned with
  [context/checklists/guidelines-rollout.md](/context/checklists/guidelines-rollout.md) so assistants can see progress
  at a glance.
