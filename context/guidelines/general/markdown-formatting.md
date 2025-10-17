# Markdown Formatting Guidelines

This repo follows common conventions inspired by the GitHub Markdown style guide, adapted for 120-character lines and
plain ASCII spacing.

## Required Rules
- Wrap text at 120 characters or less; break long list items using a hanging indent that aligns with the marker.
- Use standard spaces only—avoid non-breaking spaces and other invisible separators.
- Keep headings on their own lines and leave a blank line before and after each heading.
- Preserve fenced code blocks exactly as written; do not wrap their contents.
- Add a blank line before and after lists, block quotes, and code fences for consistent spacing.

## Helpful Practices
- Prefer sentence case for headings and keep them concise.
- Use fenced code blocks with an info string when specifying a language (e.g., ```java).
- When linking to local files, prefer relative paths and keep URLs on a single line when possible.
- Favor descriptive link text instead of raw URLs.

## Maintenance Workflow
1. Before committing, run a formatting sweep to replace non-breaking spaces and wrap content to 120 characters:
   ```bash
   python scripts/format_markdown.py
   ```
   Use `--check` to validate formatting in CI without writing changes.
2. Spot-check the diff to confirm that code fences and tables were not altered unexpectedly.
3. If a document needs deliberate long lines (e.g., tables), capture the exception in a comment near the table for
future reference.
