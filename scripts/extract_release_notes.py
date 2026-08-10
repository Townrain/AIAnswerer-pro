#!/usr/bin/env python3
"""
Extract release notes for a given version from CHANGELOG files.

Usage:
    python3 scripts/extract_release_notes.py <changelog_path> <version> <output_path>

Version matching is lenient: "v1.7.0" or "1.7.0" both match a section
"## [1.7.0]" or "## [1.7.0] - 2026-07-27". The section body runs until the
next "## [" heading or end of file. If no section matches, exits with 1.
"""

import re
import sys


def extract_section(changelog_path: str, version: str) -> str:
    """Return the markdown body for the given version section, or None."""
    with open(changelog_path, encoding="utf-8") as f:
        lines = f.readlines()

    # Normalize version: strip leading 'v' / 'V'
    norm = version.lower().lstrip("v")

    heading_re = re.compile(r"^##\s+\[([^\]]+)\]")
    in_section = False
    body: list[str] = []

    for line in lines:
        m = heading_re.match(line)
        if m:
            section_version = m.group(1).strip().lower().lstrip("v")
            if in_section:
                # Reached next section: stop
                break
            if section_version == norm:
                in_section = True
            continue
        if in_section:
            body.append(line)

    if not in_section or not body:
        return ""

    # Trim leading blank lines / separators, keep trailing structure
    while body and not body[0].strip():
        body.pop(0)
    return "".join(body).rstrip() + "\n"


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2

    changelog_path, version, output_path = sys.argv[1:4]
    section = extract_section(changelog_path, version)

    if not section:
        print(f"warning: no changelog section found for version '{version}' in {changelog_path}",
              file=sys.stderr)
        return 1

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(section)
    print(f"extracted {len(section)} chars for {version} -> {output_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
