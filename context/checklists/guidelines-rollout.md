# Guidelines Rollout Checklist

Use this checklist to track completion of the guideline coverage areas defined in
[context/roadmap/guidelines-plan.md](../roadmap/guidelines-plan.md). Update it whenever documentation changes so Codex
assistants can see the current status at a glance.

## Status table

| Area | Authoritative references | Status | Follow-up actions |
| --- | --- | --- | --- |
| Source structure & ownership | [guidelines/project-overview.md](../guidelines/project-overview.md#1-source-structure--ownership), [guidelines/general/contributor-guidelines.md](../guidelines/general/contributor-guidelines.md), [guidelines/icli/project-conventions.md](../guidelines/icli/project-conventions.md) | ✅ Complete | Maintain links when adding new modules or scoped agents. |
| Coding standards | [guidelines/project-overview.md](../guidelines/project-overview.md#2-coding-standards), [guidelines/coding/standards.md](../guidelines/coding/standards.md), [guidelines/icli/assistant-notes.md](../guidelines/icli/assistant-notes.md) | ✅ Complete | Review Spotless/Palantir upgrades periodically. |
| Build, tooling, automation | [guidelines/project-overview.md](../guidelines/project-overview.md#3-build-tooling-and-automation), [workflow/quality-gates.md](../workflow/quality-gates.md), [AGENTS.md](../../AGENTS.md) | ✅ Complete | Extend quality gates as new Gradle tasks or scripts emerge. |
| Testing expectations | [guidelines/project-overview.md](../guidelines/project-overview.md#4-testing-expectations), [testing/strategy.md](../testing/strategy.md) | ✅ Complete | Add fixture catalogue entries as new harnesses arrive; schedule Windows CI enablement (ICLI-011). |
| Documentation practices | [guidelines/project-overview.md](../guidelines/project-overview.md#5-documentation-practices), [context/README.md](../README.md), [tasks/README.md](../tasks/README.md) | ✅ Complete | Keep knowledge base references current, especially for assistant-focused docs. |
| Collaboration workflow | [guidelines/project-overview.md](../guidelines/project-overview.md#6-collaboration-workflow), [workflow/collaboration.md](../workflow/collaboration.md), [workflow/releases.md](../workflow/releases.md), [checklists/session-completion.md](session-completion.md) | ✅ Complete | Ensure automation checkpoints stay aligned with PR expectations. |

## Update log

- **2025-11-02:** Initial checklist created while delivering ICLI-022. Build/tooling, testing, and collaboration rows
  were completed after publishing quality-gate guidance and refreshed workflow notes; future updates should track CI and
  fixture additions.
