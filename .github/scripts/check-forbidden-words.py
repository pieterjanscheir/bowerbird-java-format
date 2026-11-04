#!/usr/bin/env python3
"""Fails when a forbidden word appears in the repository, its history or the pull request.

The words come from the FORBIDDEN_WORDS environment variable (one per line, matched case-insensitively), which CI
fills from a repository secret, so the words themselves never appear in the repository or in the logs. This script
only ever prints *where* a word was found (file, commit, field), never the word or the matching text.

Checked:
  - every tracked file at HEAD (contents and paths)
  - every commit message reachable from HEAD
  - the branch name, and the pull request title and description (from the environment, when set)
"""

import os
import subprocess
import sys


def git(*args: str) -> bytes:
    return subprocess.run(["git", *args], check=True, capture_output=True).stdout


def main() -> int:
    words = [w.strip().lower() for w in os.environ.get("FORBIDDEN_WORDS", "").splitlines() if w.strip()]
    if not words:
        print("::error::The FORBIDDEN_WORDS secret is not set, so this check cannot run.")
        return 1

    def contains(text: str) -> bool:
        lowered = text.lower()
        return any(w in lowered for w in words)

    problems = []

    for path in git("ls-files", "-z").decode().split("\0"):
        if not path:
            continue
        if contains(path):
            problems.append(f"file path: {path}")
            continue
        try:
            with open(path, "rb") as f:
                content = f.read().decode("utf-8", errors="ignore")
        except (IsADirectoryError, FileNotFoundError):
            continue
        if contains(content):
            problems.append(f"file contents: {path}")

    for entry in git("log", "-z", "--format=%H%n%B", "HEAD").decode("utf-8", errors="ignore").split("\0"):
        sha, _, message = entry.strip().partition("\n")
        if sha and contains(message):
            problems.append(f"commit message: {sha}")

    for field in ("BRANCH_NAME", "PR_TITLE", "PR_BODY"):
        if contains(os.environ.get(field, "")):
            problems.append(f"{field.lower().replace('_', ' ')}")

    if problems:
        print("::error::A forbidden word was found. Locations (the word itself is not shown):")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(f"No forbidden words found ({len(words)} word(s) checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
