#!/usr/bin/env python3
"""
Normalize Markdown links according to repository policy.

- Links that target files inside task-specific directories (e.g.,
  `context/tasks/ICLI-019/...` or `context/tasks/archive/ICLI-019/...`) should
  remain relative so dossiers can relocate without breaking navigation.
- Every other internal link must use an absolute path from the repository root
  (e.g., `context/roadmap/project-roadmap.md`), avoiding `../` hops that tend to
  drift during formatting.

Usage:
    python scripts/normalize_markdown_links.py
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Iterable, Tuple
from urllib.parse import quote, urlsplit, urlunsplit, unquote

TASK_ID_PATTERN = re.compile(r"^[A-Z0-9]+-[A-Z0-9-]+$")

REPO_ROOT = Path(__file__).resolve().parent.parent

INLINE_PATTERN = re.compile(r"(\[[^\]]+\]\()([^\s)]+)(\))")
REF_PATTERN = re.compile(r"^(\[[^\]]+\]:\s+)(\S+)", re.MULTILINE)
SKIP_PREFIXES: tuple[str, ...] = ("http://", "https://", "mailto:", "#", "ftp://", "urn:", "data:")


def should_skip(url: str) -> bool:
    if not url:
        return True
    if url.startswith(SKIP_PREFIXES) or url.startswith("//"):
        return True
    if url.startswith(("`", "<")):
        return True
    return False


def is_task_specific_path(rel_path: Path) -> bool:
    parts = rel_path.parts
    if len(parts) < 4:
        return False
    if parts[0] != "context" or parts[1] != "tasks":
        return False

    if parts[2] == "archive":
        if len(parts) < 5:
            return False
        task_id = parts[3]
        return bool(TASK_ID_PATTERN.match(task_id))

    task_id = parts[2]
    return bool(TASK_ID_PATTERN.match(task_id))


def adjust_missing_target(rel_path: Path) -> Path | None:
    parts = rel_path.parts
    if len(parts) >= 3 and parts[0] == "context" and parts[1] == "tasks":
        candidate = Path("context", *parts[2:])
        candidate_abs = REPO_ROOT / candidate
        if candidate_abs.exists():
            return candidate
    return None


def format_task_relative(base: Path, target: Path) -> str:
    return os.path.relpath(target, base).replace("\\", "/")


def format_root_absolute(rel_path: Path) -> str:
    path = rel_path.as_posix()
    if not path.startswith("/"):
        path = f"/{path}"
    return path


def fix_url(file_path: Path, url: str) -> Tuple[str, bool]:
    if should_skip(url):
        return url, False

    base = file_path.parent
    split = urlsplit(url)
    if split.scheme or split.netloc:
        return url, False

    path = split.path
    if not path:
        return url, False

    decoded_path = unquote(path)
    root_relative = False
    if decoded_path.startswith("/"):
        root_relative = True
        decoded_path = decoded_path.lstrip("/")
    elif decoded_path.startswith("context/"):
        root_relative = True

    path_obj = Path(decoded_path)
    if root_relative:
        joined = (REPO_ROOT / path_obj).resolve()
    else:
        joined = (base / path_obj).resolve()

    try:
        rel_abs = joined.relative_to(REPO_ROOT)
    except ValueError:
        return url, False

    target_abs = REPO_ROOT / rel_abs
    if not target_abs.exists():
        adjusted = adjust_missing_target(rel_abs)
        if adjusted is None:
            return url, False
        rel_abs = adjusted
        target_abs = REPO_ROOT / rel_abs
        if not target_abs.exists():
            return url, False

    if is_task_specific_path(rel_abs):
        new_path = format_task_relative(base, target_abs)
    else:
        new_path = format_root_absolute(rel_abs)

    encoded_path = quote(new_path, safe="/-._~")
    new_url = urlunsplit((split.scheme, split.netloc, encoded_path, split.query, split.fragment))

    if new_url == url:
        return url, False
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
