import re
import subprocess
import sys
from fnmatch import fnmatch
from typing import NamedTuple

MAX_LINES = 200
MAX_FILES = 10
EXEMPT = ("app/src/test/resources/*", "quality/*-baseline.xml")
BRACED_RENAME = re.compile(r"\{[^{}]* => ([^{}]*)\}")


class Change(NamedTuple):
    path: str
    lines: int


def renamed_target(path: str) -> str:
    if "{" in path:
        return BRACED_RENAME.sub(r"\1", path).replace("//", "/")
    return path.split(" => ")[-1]


def parse_numstat(text: str) -> list:
    return [parse_row(row) for row in text.splitlines() if row.strip()]


def parse_row(row: str) -> Change:
    added, deleted, path = row.split("\t", 2)
    lines = 0 if added == "-" else int(added) + int(deleted)
    return Change(renamed_target(path), lines)


def counted(changes: list) -> list:
    return [c for c in changes if not any(fnmatch(c.path, pattern) for pattern in EXEMPT)]


def violations(changes: list) -> list:
    relevant = counted(changes)
    lines = sum(c.lines for c in relevant)
    found = []
    if lines > MAX_LINES:
        found.append(f"изменено {lines} строк при пределе {MAX_LINES}: разделите изменение")
    if len(relevant) > MAX_FILES:
        found.append(f"изменено {len(relevant)} файлов при пределе {MAX_FILES}: разделите изменение")
    return found


def numstat_since(base: str) -> str:
    command = ["git", "diff", "--numstat", "-M", f"{base}...HEAD"]
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def main(base: str) -> int:
    found = violations(parse_numstat(numstat_since(base)))
    for message in found:
        print(f"::error title=Размер PR::{message}")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "origin/main"))
