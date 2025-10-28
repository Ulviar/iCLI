#!/usr/bin/env python3
"""List files that were added or modified since the last commit."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Iterable, Set


def run_git(args: Iterable[str]) -> str:
    """Execute a git command and return stdout as text."""
    result = subprocess.run(
        ["git", *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def gather_paths(base_ref: str) -> Set[Path]:
    """Collect tracked and untracked paths relative to the repository root."""
    tracked_output = run_git(["diff", "--name-only", "--diff-filter=AM", base_ref])
    untracked_output = run_git(["ls-files", "--others", "--exclude-standard"])

    tracked = {Path(path.strip()) for path in tracked_output.splitlines() if path.strip()}
    untracked = {Path(path.strip()) for path in untracked_output.splitlines() if path.strip()}
    return tracked | untracked


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default="HEAD",
        help="Git revision to diff against (default: HEAD).",
    )
    parser.add_argument(
        "--absolute",
        action="store_true",
        help="Emit absolute paths instead of repository-relative paths.",
    )
    parser.add_argument(
        "--null",
        action="store_true",
        help="Separate paths with a null byte instead of newlines.",
    )
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        repo_root = Path(run_git(["rev-parse", "--show-toplevel"]).strip())
    except subprocess.CalledProcessError as exc:
        print(exc.stderr or "fatal: not a git repository", file=sys.stderr)
        return exc.returncode or 1

    try:
        paths = sorted(gather_paths(args.base))
    except subprocess.CalledProcessError as exc:
        print(exc.stderr or "fatal: git command failed", file=sys.stderr)
        return exc.returncode or 1

    if not paths:
        return 0

    if args.null:
        for index, path in enumerate(paths):
            resolved = repo_root / path if args.absolute else path
            # Use sys.stdout.buffer for null-separated output to avoid encoding overhead.
            sys.stdout.buffer.write(str(resolved).encode("utf-8"))
            if index < len(paths) - 1:
                sys.stdout.buffer.write(b"\0")
        sys.stdout.buffer.flush()
        return 0

    for path in paths:
        resolved = repo_root / path if args.absolute else path
        print(resolved)
    return 0


if __name__ == "__main__":
    sys.exit(main())
