import re
import subprocess
import sys
from pathlib import Path

DOCS = ("CLAUDE.md", "docs/quality-roadmap.md")
DEFINITION = re.compile(
    r"^[+-](?![+-]).*?\b(?:fun\s+interface|fun|def|class|object|interface)\s+(?:<[^>]*>\s*)?"
    r"(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?`?([A-Za-z_]\w*)",
    re.MULTILINE,
)
KOTLIN_TEST = re.compile(r"^app/src/test/java/(.+)Test\.kt$")
PYTHON_TEST = re.compile(r"^(tools/(?:.+/)?)test_([^/]+\.py)$")
MAX_LINES = 600
MAX_CALLS = 20
FENCE = "`````"


def changed_names(diff: str) -> list:
    return sorted(set(DEFINITION.findall(diff)))


def call_lines(grep: str, changed: list) -> list:
    rows = [line for line in grep.splitlines() if line.split(":", 1)[0] not in changed]
    return rows[:MAX_CALLS]


def tested_source(path: str) -> str:
    kotlin, python = KOTLIN_TEST.match(path), PYTHON_TEST.match(path)
    if kotlin:
        return f"app/src/main/java/{kotlin.group(1)}.kt"
    return f"{python.group(1)}{python.group(2)}" if python else ""


def calls_section(calls: dict) -> str:
    blocks = [f"### {name}\n\n{FENCE}\n" + "\n".join(rows) + f"\n{FENCE}\n" for name, rows in calls.items() if rows]
    return "\n".join(blocks) or "Мест вызова вне изменённых файлов нет.\n"


def file_section(path: str, text: str) -> str:
    size = len(text.splitlines())
    if size > MAX_LINES:
        return f"### {path}\n\n{size} строк, целиком не приложен: смотри дифф.\n"
    numbered = "\n".join(f"{n:>5}  {row}" for n, row in enumerate(text.splitlines(), 1))
    return f"### {path} (номер строки слева)\n\n{FENCE}\n{numbered}\n{FENCE}\n"


def context(description: str, diff: str, files: dict, calls: dict) -> str:
    sections = "\n".join(file_section(path, text) for path, text in files.items())
    return "\n\n".join(
        [
            f"# Описание PR\n\n{description.rstrip()}",
            f"# Дифф\n\n{FENCE}diff\n{diff.rstrip()}\n{FENCE}",
            f"# Изменённые файлы и код под тестом целиком\n\n{sections}",
            f"# Места вызова изменённых определений\n\n{calls_section(calls)}",
        ]
    )


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def grep_word(name: str) -> str:
    command = ["git", "grep", "-n", "-I", "-w", name, "--", ".", ":(exclude)quality"]
    return subprocess.run(command, check=False, capture_output=True, text=True).stdout


def text_files(numstat: str) -> list:
    rows = [line.split("\t") for line in numstat.splitlines()]
    return [path for added, _, path in rows if added != "-"]


def main(base: str, description_path: str) -> int:
    diff = git("diff", f"{base}...HEAD")
    numstat = git("diff", "--numstat", "--no-renames", f"{base}...HEAD")
    changed = text_files(numstat)
    sources = [tested_source(path) for path in changed]
    present = [path for path in [*DOCS, *changed, *sources] if path and Path(path).is_file()]
    files = {path: Path(path).read_text(encoding="utf-8") for path in dict.fromkeys(present)}
    calls = {name: call_lines(grep_word(name), list(files)) for name in changed_names(diff)}
    description = Path(description_path).read_text(encoding="utf-8")
    Path(".ai-review-pr.md").write_text(context(description, diff, files, calls), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
