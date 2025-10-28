#!/usr/bin/env python3
"""Run mandatory pre-response checks for Codex sessions with pending edits."""

from __future__ import annotations

import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence

MARKDOWN_EXTENSIONS = {".md"}
CODE_EXTENSIONS = {
    ".java",
    ".kt",
    ".kts",
    ".py",
    ".sh",
    ".gradle",
    ".groovy",
    ".xml",
}


@dataclass
class CommandResult:
    command: Sequence[str]
    returncode: int
    stdout: str
    stderr: str


def run(cmd: Sequence[str], cwd: Path) -> CommandResult:
    """Execute a command and capture its output."""
    completed = subprocess.run(
        cmd,
        cwd=cwd,
        capture_output=True,
        text=True,
    )
    return CommandResult(cmd, completed.returncode, completed.stdout, completed.stderr)


def print_section(title: str) -> None:
    print(f"\n== {title} ==")


def gather_changed_files(python: str, repo_root: Path) -> List[Path]:
    result = run([python, str(repo_root / "scripts" / "list_changed_files.py")], repo_root)
    if result.returncode != 0:
        sys.stderr.write("Failed to list changed files:\n")
        sys.stderr.write(result.stderr)
        sys.exit(result.returncode or 1)
    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    return [Path(line) for line in lines]


def filter_by_extensions(paths: Iterable[Path], extensions: Iterable[str]) -> List[Path]:
    normalized = {ext.lower() for ext in extensions}
    matched: List[Path] = []
    for path in paths:
        if path.suffix.lower() in normalized:
            matched.append(path)
    return matched


def ensure_commit_message(repo_root: Path) -> None:
    candidate = repo_root / ".commit-message"
    print_section(".commit-message verification")
    if not candidate.exists():
        print("WARNING: .commit-message is missing. Populate it before ending the session.")
        return
    if not candidate.read_text(encoding="utf-8").strip():
        print("WARNING: .commit-message is empty. Refresh it with the current diff summary.")
        return
    print(".commit-message present and non-empty.")


def main() -> int:
    try:
        repo_root = Path(
            run(["git", "rev-parse", "--show-toplevel"], Path.cwd()).stdout.strip()
        )
    except Exception as exc:
        sys.stderr.write(f"Unable to determine repository root: {exc}\n")
        return 1

    python = sys.executable or "python3"

    print_section("git status -sb")
    status = run(["git", "status", "-sb"], repo_root)
    if status.returncode != 0:
        sys.stderr.write(status.stderr)
        return status.returncode
    print(status.stdout.rstrip() or "(clean)")

    print_section("Changed files vs HEAD")
    changed_paths = gather_changed_files(python, repo_root)
    if not changed_paths:
        print("No tracked or untracked changes detected.")
        ensure_commit_message(repo_root)
        return 0
    for path in changed_paths:
        print(path)

    markdown_paths = filter_by_extensions(changed_paths, MARKDOWN_EXTENSIONS)
    code_paths = filter_by_extensions(changed_paths, CODE_EXTENSIONS)

    if markdown_paths:
        print_section("Formatting Markdown")
        cmd = [python, str(repo_root / "scripts" / "format_markdown.py")]
        cmd.extend(str(path) for path in markdown_paths)
        result = run(cmd, repo_root)
        if result.returncode != 0:
            sys.stderr.write("Markdown formatting failed:\n")
            sys.stderr.write(result.stderr)
            return result.returncode
        if result.stdout.strip():
            print(result.stdout.strip())
        print("Markdown formatting completed.")
    else:
        print_section("Formatting Markdown")
        print("No Markdown files detected among changes.")

    if code_paths:
        print_section("Required Gradle tasks (run via MCP)")
        print("- execute_gradle_task → tasks: ['spotlessApply']")
        print("- run_gradle_tests   → gradleTasks: ['test']")
    else:
        print_section("Required Gradle tasks (run via MCP)")
        print("No source files detected that require Spotless/Test runs.")

    ensure_commit_message(repo_root)

    print_section("Post-check diff")
    post_paths = gather_changed_files(python, repo_root)
    for path in post_paths:
        print(path)

    return 0


if __name__ == "__main__":
    sys.exit(main())
