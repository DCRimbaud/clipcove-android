"""Validate ClipCove's public-repository comment policy.

SPDX-License-Identifier: GPL-3.0-only
Copyright (c) 2026 DCRimbaud
Author: DCRimbaud

The checker is intentionally conservative: it validates required file headers,
public-contract documentation, English-only first-party comments, and test
contract notes. It does not attempt to judge prose quality.
"""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_PARTS = {"build", "__pycache__", "third_party"}
THIRD_PARTY_FILES = {"app/src/main/python/douyin/abogus.py"}
HAN = re.compile(r"[\u4e00-\u9fff]")
KOTLIN_DECLARATION = re.compile(
    r"^\s*(?:public\s+|internal\s+)?"
    r"(?:(?:data|sealed|enum|abstract|open|value|fun)\s+)*"
    r"(?:class|interface|object|fun)\s+([A-Za-z_][A-Za-z0-9_]*)"
)
KOTLIN_TEST = re.compile(r"^\s*fun `([^`]+)`\s*\(")


def first_party_files(suffixes: tuple[str, ...]) -> list[Path]:
    """Return first-party source files with generated and third-party paths removed."""
    files: list[Path] = []
    for suffix in suffixes:
        for path in ROOT.rglob(f"*{suffix}"):
            relative = path.relative_to(ROOT).as_posix()
            if any(part in EXCLUDED_PARTS for part in path.parts):
                continue
            if relative in THIRD_PARTY_FILES:
                continue
            files.append(path)
    return sorted(set(files))


def has_header(path: Path) -> bool:
    """Return whether the first lines contain an SPDX identifier and public author."""
    prefix = "\n".join(path.read_text(encoding="utf-8").splitlines()[:12])
    return (
        "SPDX-License-Identifier: GPL-3.0-only" in prefix
        and "Author: DCRimbaud" in prefix
    )


def chinese_comment_lines(path: Path) -> list[int]:
    """Find Han characters inside comment lines for the relevant source language."""
    lines = path.read_text(encoding="utf-8").splitlines()
    hits: list[int] = []
    for number, line in enumerate(lines, 1):
        stripped = line.lstrip()
        is_comment = stripped.startswith(("#", "//", "/*", "*"))
        if is_comment and HAN.search(line):
            hits.append(number)
    return hits


def kotlin_doc_errors(path: Path) -> list[str]:
    """Find undocumented Kotlin declarations and test methods."""
    lines = path.read_text(encoding="utf-8").splitlines()
    errors: list[str] = []
    for index, line in enumerate(lines):
        match = KOTLIN_DECLARATION.match(line)
        test_match = KOTLIN_TEST.match(line)
        if not match and not test_match:
            continue
        previous = index - 1
        while previous >= 0 and (
            lines[previous].lstrip().startswith("@") or not lines[previous].strip()
        ):
            previous -= 1
        documented = previous >= 0 and lines[previous].lstrip().endswith("*/")
        if not documented:
            errors.append(f"{path.relative_to(ROOT)}:{index + 1}: missing KDoc")
    return errors


def python_doc_errors(path: Path) -> list[str]:
    """Find public Python classes/functions without a Docstring."""
    try:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except SyntaxError as error:
        return [f"{path.relative_to(ROOT)}:{error.lineno}: syntax error"]
    errors: list[str] = []
    module_doc = ast.get_docstring(tree, clean=False)
    if module_doc is None:
        errors.append(f"{path.relative_to(ROOT)}:1: missing module Docstring")
    elif HAN.search(module_doc):
        errors.append(f"{path.relative_to(ROOT)}:1: Chinese module Docstring")
    for node in ast.walk(tree):
        if not isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        if node.name.startswith("_"):
            continue
        docstring = ast.get_docstring(node, clean=False)
        if docstring is None:
            errors.append(
                f"{path.relative_to(ROOT)}:{node.lineno}: missing Docstring for {node.name}"
            )
        elif HAN.search(docstring):
            errors.append(
                f"{path.relative_to(ROOT)}:{node.lineno}: Chinese Docstring for {node.name}"
            )
    return errors


def main() -> int:
    """Run all comment-policy checks and return a process exit code."""
    errors: list[str] = []
    sources = first_party_files((".kt", ".py"))
    for path in sources:
        if not has_header(path):
            errors.append(f"{path.relative_to(ROOT)}: missing SPDX/Author header")
        errors.extend(
            f"{path.relative_to(ROOT)}:{line}: Chinese comment in public source"
            for line in chinese_comment_lines(path)
        )
        if path.suffix == ".kt":
            errors.extend(kotlin_doc_errors(path))
        else:
            errors.extend(python_doc_errors(path))
    if errors:
        print("Comment policy violations:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"Comment policy OK ({len(sources)} first-party source files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
