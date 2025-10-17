#!/usr/bin/env python3
"""Normalize Markdown files by replacing non-breaking spaces and wrapping text."""

from __future__ import annotations

import argparse
import re
import sys
import textwrap
from pathlib import Path
from typing import Iterable, List

NBSP_REPLACEMENTS = {
    "\u00A0": " ",
    "\u202F": " ",
}

LIST_RE = re.compile(r"^(\s*)([-*+])\s+(.*)$")
ORDERED_RE = re.compile(r"^(\s*)(\d+\.)(\s+)(.*)$")
BLOCK_QUOTE_RE = re.compile(r"^(>+)\s?(.*)$")
FENCE_RE = re.compile(r"^(```|~~~)")
INDENTED_RE = re.compile(r"^(\s{1,3})(\S.*)$")
H_RULES = {"---", "***", "___"}
TABLE_ROW_RE = re.compile(r"^\s*\|.*\|\s*$")


def gather_markdown(paths: Iterable[str]) -> List[Path]:
    candidates: List[Path] = []
    for raw in paths:
        path = Path(raw)
        if not path.exists():
            continue
        if path.is_file() and path.suffix.lower() == ".md":
            candidates.append(path)
            continue
        if path.is_dir():
            candidates.extend(sorted(p for p in path.rglob("*.md") if p.is_file()))
    # Remove duplicates while preserving order.
    seen = set()
    unique: List[Path] = []
    for item in candidates:
        resolved = item.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        unique.append(item)
    return unique


def normalize_spaces(text: str) -> str:
    for src, dst in NBSP_REPLACEMENTS.items():
        text = text.replace(src, dst)
    return text


def format_lines(text: str, width: int) -> str:
    lines = text.splitlines()
    formatted: List[str] = []
    in_fence = False

    for raw in lines:
        line = raw.rstrip()
        stripped = line.strip()

        if FENCE_RE.match(stripped):
            formatted.append(line if line else stripped)
            in_fence = not in_fence
            continue
        if in_fence:
            formatted.append(line)
            continue
        if not stripped:
            formatted.append("")
            continue
        if stripped.startswith("#"):
            formatted.append(stripped)
            continue
        if stripped in H_RULES:
            formatted.append(stripped)
            continue
        if TABLE_ROW_RE.match(line):
            formatted.append(line)
            continue
        if line.startswith("    "):
            formatted.append(line)
            continue

        indent_match = INDENTED_RE.match(line)
        if indent_match:
            indent, content = indent_match.groups()
            formatted.append(
                textwrap.fill(
                    content.strip(),
                    width=width,
                    initial_indent=indent,
                    subsequent_indent=indent,
                    break_long_words=False,
                    break_on_hyphens=False,
                    replace_whitespace=False,
                )
            )
            continue

        list_match = LIST_RE.match(stripped)
        if list_match:
            indent, marker, content = list_match.groups()
            formatted.append(
                textwrap.fill(
                    content,
                    width=width,
                    initial_indent=f"{indent}{marker} ",
                    subsequent_indent=f"{indent}{' ' * (len(marker) + 1)}",
                    break_long_words=False,
                    break_on_hyphens=False,
                    replace_whitespace=False,
                )
            )
            continue

        ordered_match = ORDERED_RE.match(stripped)
        if ordered_match:
            indent, marker, _, content = ordered_match.groups()
            marker_with_space = f"{marker} "
            formatted.append(
                textwrap.fill(
                    content,
                    width=width,
                    initial_indent=f"{indent}{marker_with_space}",
                    subsequent_indent=f"{indent}{' ' * len(marker_with_space)}",
                    break_long_words=False,
                    break_on_hyphens=False,
                    replace_whitespace=False,
                )
            )
            continue

        quote_match = BLOCK_QUOTE_RE.match(stripped)
        if quote_match:
            markers, content = quote_match.groups()
            prefix = "> " * len(markers)
            base_indent = prefix if prefix else "> "
            formatted.append(
                textwrap.fill(
                    content,
                    width=width,
                    initial_indent=base_indent,
                    subsequent_indent=base_indent,
                    break_long_words=False,
                    break_on_hyphens=False,
                    replace_whitespace=False,
                )
            )
            continue

        formatted.append(
            textwrap.fill(
                stripped,
                width=width,
                break_long_words=False,
                break_on_hyphens=False,
                replace_whitespace=False,
            )
        )

    return "\n".join(formatted) + "\n"


def process_file(path: Path, width: int, check: bool) -> bool:
    original = path.read_text(encoding="utf-8")
    normalized = normalize_spaces(original)
    formatted = format_lines(normalized, width)
    if original == formatted:
        return True
    if check:
        return False
    path.write_text(formatted, encoding="utf-8")
    return True


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", default=["."], help="Files or directories to format.")
    parser.add_argument("--width", type=int, default=120, help="Maximum line width (default: 120).")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not modify files, just exit non-zero if formatting would change anything.",
    )
    args = parser.parse_args(list(argv) if argv is not None else None)

    markdown_files = gather_markdown(args.paths)
    if not markdown_files:
        return 0

    success = True
    for path in markdown_files:
        ok = process_file(path, args.width, args.check)
        if not ok:
            success = False
            print(path)

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
INDENTED_RE = re.compile(r"^(\s{1,3})(\S.*)$")
