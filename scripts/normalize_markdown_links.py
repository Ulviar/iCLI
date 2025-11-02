#!/usr/bin/env python3
"""
Normalize relative links inside Markdown files.

The script scans every *.md file under the repository root, looking for links
that point to archived dossiers (e.g., "../archive/..."), then rewrites them so
they resolve correctly from their current location. It also fixes inline links
in active dossiers that reference archived content.

Usage:
    python scripts/normalize_markdown_links.py
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parent.parent

INLINE_PATTERN = re.compile(r"(\[[^\]]+\]\()([^\s)]+)(\))")
REF_PATTERN = re.compile(r"^(\[[^\]]+\]:\s+)(\S+)", re.MULTILINE)
SKIP_PREFIXES: tuple[str, ...] = ("http://", "https://", "mailto:", "#", "ftp://", "urn:", "data:")
COMPONENTS_TO_STRIP: set[str] = {"tasks", "archive"}


def should_skip(url: str) -> bool:
    if not url:
        return True
    if url.startswith(SKIP_PREFIXES) or url.startswith("//"):
        return True
    if url.startswith(("`", "<")):
        return True
    return False


def fix_url(file_path: Path, url: str) -> tuple[str, bool]:
    if should_skip(url):
        return url, False

    base = file_path.parent
    path_obj = Path(url)
    if path_obj.is_absolute():
        return url, False

    joined = (base / path_obj).resolve()

    try:
        rel_abs = joined.relative_to(REPO_ROOT)
    except ValueError:
        return url, False

    abs_path = REPO_ROOT / rel_abs
    if abs_path.exists():
        return url, False

    parts = list(rel_abs.parts)
    if not parts or parts[0] != "context":
        return url, False

    idx = 1
    while idx < len(parts) and parts[idx] in COMPONENTS_TO_STRIP:
        idx += 1

    candidate_parts = ("context", *parts[idx:])
    candidate_rel = Path(*candidate_parts)
    candidate_abs = REPO_ROOT / candidate_rel
    if not candidate_abs.exists():
        return url, False

    new_url = os.path.relpath(candidate_abs, base).replace("\\", "/")
    return new_url, True


def rewrite_links(file_path: Path) -> bool:
    text = file_path.read_text(encoding="utf-8")
    changed = False

    def replace_inline(match: re.Match[str]) -> str:
        nonlocal changed
        url = match.group(2)
        new_url, did_change = fix_url(file_path, url)
        if did_change:
            changed = True
            return f"{match.group(1)}{new_url}{match.group(3)}"
        return match.group(0)

    def replace_ref(match: re.Match[str]) -> str:
        nonlocal changed
        url = match.group(2)
        new_url, did_change = fix_url(file_path, url)
        if did_change:
            changed = True
            return f"{match.group(1)}{new_url}"
        return match.group(0)

    new_text = INLINE_PATTERN.sub(replace_inline, text)
    new_text = REF_PATTERN.sub(replace_ref, new_text)

    if changed:
        file_path.write_text(new_text, encoding="utf-8")
    return changed


def gather_markdown_files(root: Path) -> Iterable[Path]:
    return (p for p in root.rglob("*.md") if p.is_file())


def main() -> None:
    modified = []
    for md_file in gather_markdown_files(REPO_ROOT):
        if rewrite_links(md_file):
            modified.append(md_file.relative_to(REPO_ROOT))

    if modified:
        print("Updated Markdown links in:")
        for path in sorted(modified):
            print(f"  {path}")
    else:
        print("All Markdown links are already normalized.")


if __name__ == "__main__":
    main()
