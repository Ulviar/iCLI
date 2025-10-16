# Repository Agent Guidelines

## Scope
These guidelines apply to the entire repository unless a more specific `AGENTS.md` is created within a subdirectory.

## Tooling and Environment
- Primary production language: **Java 21**.
- Test language and framework: **Kotlin 2.2.20** with **JUnit 5**.
- Build system: **Gradle 8.14.3** with Kotlin DSL (`build.gradle.kts`).
- Minimum JDK and target JVM version: **21 (LTS)**.

## Code Style
- Java sources must follow **Google Java Format** and be enforced via **Spotless**.
- Kotlin sources must follow the official **Kotlin coding conventions** and be enforced via **Spotless**.

## Quality Gates
- Configure and run **Spotless** for formatting and **SpotBugs** for static analysis as part of the build.
- All automated tests should be written in Kotlin using JUnit 5.

## Workflow Expectations
1. Prefer Gradle tasks for building, testing, formatting, and linting once the Gradle project is initialized (e.g., `./gradlew build`, `./gradlew spotlessApply`, `./gradlew spotbugsMain`).
2. Keep README and other documentation up to date when behavior or tooling changes.
3. When adding or updating build tooling or library dependencies, prefer the latest stable versions available (excluding the pinned versions of Java, Kotlin, and Gradle) by checking for updates before committing changes.

## Documentation
All project documentation, comments, and commit messages must be written in **English**.

## Repository Map
- Primary coordination instructions are kept in this file located at the repository root (`AGENTS.md`).
- When creating new modules or directories, add additional `AGENTS.md` files if extra guidance is required.

