# ICLI-040 — Prepare release-ready documentation set

## Status
- **Lifecycle stage:** Planning
- **Overall status:** Backlog
- **Last updated:** 2025-11-04
- **Owner:** Assistant

## Overview
- **Objective:** Rework every README.md (root project, samples, any feature modules) so the content targets external OSS
  consumers instead of internal contributors, covering installation, supported scenarios, and usage guidance.
- **Definition of Done:**
  1. Each README describes OSS-friendly positioning (what the module provides, who should use it, prerequisites).
  2. Quick-start instructions showcase the Essential API first, with references to Advanced/pooling paths as needed.
  3. Contribution/developer-only notes move to AGENTS or docs; READMEs remain user-facing.
  4. Formatting follows repository Markdown standards and cross-links to relevant roadmap/use-case docs.
  5. Maintainer review confirms wording is release-ready.
- **Constraints:** Follow markdown guidelines, keep documentation English-only, avoid leaking unreleased features, and
  reuse shared terminology from roadmap/use-case catalogue.
- **Roles to notify:** Maintainer

## Planning
- **Scope summary:** Audit every module README (root, samples, future docs modules) and rewrite sections (intro,
  install, scenarios, troubleshooting) so they read like public documentation.
- **Proposed deliverables:** Updated README.md files, possible new sections (Getting Started, Scenarios, Support),
  changelog references, plus cross-links to scenarios/roadmap.
- **Open questions / risks:** Need confirmation whether additional modules beyond `samples` exist; determine if README
  content should mention upcoming pooling features or stick to currently shipped APIs; ensure doc tone aligns with legal
  review requirements once release nears.
- **Backlog link:** [backlog.md](/context/tasks/backlog.md)

## Analysis
- **Log entries:** Reference dated files in `analysis/`. Example:
  - [analysis/2025-10-17.md](analysis/2025-10-17.md)
- **Knowledge consulted:** Link (using relative paths) only to specific documents that substantially shifted your plan
  compared with the initial task description, and note how each influenced your approach, including any difficulty
  locating the information.
- **Readiness decision:** State whether execution can begin or what gaps remain.

## Research
- **Requests filed:** _None yet._
- **External outputs:** _TBD._
- **Summary:** _TBD._
- **Human response:** _TBD._

## Execution
- **History entries:** _Pending._
- **Implementation highlights:** _Pending._
- **Testing:** _Pending._
- **Follow-up work:** _Pending._
- **Retrospective:** _Pending._

## Completion & archive
- **Archive status:** Active / Ready to archive / Archived (include date).
- **Archive location:** Path once the dossier is moved under `context/tasks/archive/`.
- **Final verification:** Summary of tests or approvals completed before archival.

## Decisions & notes
- **Key decisions:** _Pending._
- **Risks:** _Pending._
- **Links:** Related tasks — ICLI-037…ICLI-039 (samples docs groundwork).
