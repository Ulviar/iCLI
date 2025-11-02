#!/usr/bin/env python3
"""Normalize Markdown files by replacing non-breaking spaces and wrapping text."""

from __future__ import annotations

import argparse
import os
import re
import sys
import textwrap
from pathlib import Path
from typing import Iterable, List
from urllib.parse import urlparse

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
CODE_LINK_RE = re.compile(r"`([^`\n]+?\.(?:md|MD)(?:[#?][^`\n]+)?)`")
CODE_WRAPPED_LINK_RE = re.compile(r"`(\[[^`\]]+\]\([^`]+\))`")
MARKDOWN_LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
DOUBLE_LINK_RE = re.compile(r"\[\[([^\]]+)\]\(([^)]+)\)\]\(([^)]+)\)")

REPO_ROOT = Path(__file__).resolve().parent.parent


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


def _convert_code_link(match: re.Match[str], base: Path) -> str:
    raw = match.group(1)
    parsed = urlparse(raw)
    if parsed.scheme or parsed.netloc:
        return f"[{raw}]({raw})"

    path_part = parsed.path
    suffix = ""
    if parsed.query:
        suffix += f"?{parsed.query}"
    if parsed.fragment:
        suffix += f"#{parsed.fragment}"

    if not path_part or path_part.startswith(('/', '.', '..')):
        return f"[{raw}]({raw})"

    candidate = (REPO_ROOT / path_part).resolve()
    try:
        candidate.relative_to(REPO_ROOT)
    except ValueError:
        return f"[{raw}]({raw})"

    if not candidate.exists():
        return f"[{raw}]({raw})"

    relative = os.path.relpath(candidate, base.parent).replace(os.sep, "/")
    return f"[{raw}]({relative}{suffix})"


def normalize_double_links(text: str) -> str:
    return DOUBLE_LINK_RE.sub(lambda m: f"[{m.group(1)}]({m.group(3)})", text)


def normalize_code_links(text: str, base: Path) -> str:
    lines = text.splitlines()
    normalized: List[str] = []
    in_fence = False
    for line in lines:
        stripped = line.lstrip()
        if FENCE_RE.match(stripped):
            normalized.append(line)
            in_fence = not in_fence
            continue
        if in_fence:
            normalized.append(line)
            continue
        converted = CODE_LINK_RE.sub(lambda match: _convert_code_link(match, base), line)
        converted = CODE_WRAPPED_LINK_RE.sub(r"\1", converted)
        normalized.append(converted)
    return "\n".join(normalized)


def _path_under_repo(path: Path) -> bool:
    try:
        path.relative_to(REPO_ROOT)
        return True
    except ValueError:
        return False


def normalize_standard_links(text: str, base: Path) -> str:
    def replace(match: re.Match[str]) -> str:
        label, target = match.group(1), match.group(2)
        parsed = urlparse(target)
        if parsed.scheme or parsed.netloc or target.startswith("#"):
            return match.group(0)

        path_part = parsed.path
        suffix = ""
        if parsed.query:
            suffix += f"?{parsed.query}"
        if parsed.fragment:
            suffix += f"#{parsed.fragment}"

        if not path_part:
            return match.group(0)

        candidate = (base.parent / path_part).resolve()
        if _path_under_repo(candidate) and candidate.exists():
            relative = os.path.relpath(candidate, base.parent).replace(os.sep, "/")
            new_label = label
            if Path(path_part).name == "backlog.md":
                new_label = "backlog.md"
            return f"[{new_label}]({relative}{suffix})"

        candidate = (REPO_ROOT / path_part).resolve()
        if _path_under_repo(candidate) and candidate.exists():
            relative = os.path.relpath(candidate, base.parent).replace(os.sep, "/")
            new_label = label
            if Path(path_part).name == "backlog.md":
                new_label = "backlog.md"
            return f"[{new_label}]({relative}{suffix})"

        return match.group(0)

    return MARKDOWN_LINK_RE.sub(replace, text)


def format_lines(text: str, width: int) -> str:
    lines = text.splitlines()
    formatted: List[str] = []
    in_fence = False
    paragraph_buffer: List[str] = []
    initial_indent = ""
    subsequent_indent = ""

    def flush_paragraph() -> None:
        nonlocal initial_indent, subsequent_indent
        if not paragraph_buffer:
            return
        paragraph_text = " ".join(paragraph_buffer)
        formatted.append(
            textwrap.fill(
                paragraph_text,
                width=width,
                initial_indent=initial_indent,
                subsequent_indent=subsequent_indent or initial_indent,
                break_long_words=False,
                break_on_hyphens=False,
                replace_whitespace=False,
            )
        )
        paragraph_buffer.clear()
        initial_indent = ""
        subsequent_indent = ""

    for raw in lines:
        line = raw.rstrip()
        stripped = line.strip()

        if FENCE_RE.match(stripped):
            flush_paragraph()
            formatted.append(line if line else stripped)
            in_fence = not in_fence
            continue
        if in_fence:
            formatted.append(line)
            continue
        if not stripped:
            flush_paragraph()
            formatted.append("")
            continue
        if stripped.startswith("#"):
            flush_paragraph()
            formatted.append(stripped)
            continue
        if stripped in H_RULES:
            flush_paragraph()
            formatted.append(stripped)
            continue
        if TABLE_ROW_RE.match(line):
            flush_paragraph()
            formatted.append(line)
            continue
        if paragraph_buffer and subsequent_indent and line.startswith(subsequent_indent) and stripped:
            if not LIST_RE.match(stripped) and not ORDERED_RE.match(stripped):
                paragraph_buffer.append(stripped)
                continue
        if line.startswith("    "):
            flush_paragraph()
            formatted.append(line)
            continue

        indent_match = INDENTED_RE.match(line)
        if indent_match:
            flush_paragraph()
            indent, content = indent_match.groups()
            initial_indent = indent
            subsequent_indent = indent
            paragraph_buffer.append(content.strip())
            continue

        list_match = LIST_RE.match(stripped)
        if list_match:
            flush_paragraph()
            indent, marker, content = list_match.groups()
            initial_indent = f"{indent}{marker} "
            subsequent_indent = f"{indent}{' ' * (len(marker) + 1)}"
            paragraph_buffer.append(content)
            continue

        ordered_match = ORDERED_RE.match(stripped)
        if ordered_match:
            flush_paragraph()
            indent, marker, _, content = ordered_match.groups()
            marker_with_space = f"{marker} "
            initial_indent = f"{indent}{marker_with_space}"
            subsequent_indent = f"{indent}{' ' * len(marker_with_space)}"
            paragraph_buffer.append(content)
            continue

        quote_match = BLOCK_QUOTE_RE.match(stripped)
        if quote_match:
            flush_paragraph()
            markers, content = quote_match.groups()
            prefix = "> " * len(markers)
            base_indent = prefix if prefix else "> "
            initial_indent = base_indent
            subsequent_indent = base_indent
            paragraph_buffer.append(content)
            continue

        if not paragraph_buffer:
            initial_indent = ""
            subsequent_indent = ""

        paragraph_buffer.append(stripped)

    flush_paragraph()
    return "\n".join(formatted) + "\n"


def process_file(path: Path, width: int, check: bool) -> bool:
    original = path.read_text(encoding="utf-8")
    normalized = normalize_spaces(original)
    normalized = normalize_double_links(normalized)
    normalized = normalize_code_links(normalized, path)
    normalized = normalize_standard_links(normalized, path)
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
