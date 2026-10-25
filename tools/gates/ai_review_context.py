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
CALL = re.compile(r"\b([a-z_]\w*)\s*\(")
COMMON_WORDS = (
    "if for while when return catch fun def super this print println require check error listOf mapOf setOf "
    "emptyList len str int isinstance range sorted enumerate assertThat assertEqual"
)
COMMON = frozenset(re.findall(r"\w+", COMMON_WORDS))
MAX_LINES = 600
MAX_CALLS = 20
MAX_CALLEES = 15
SNIPPET = 20
HISTORY = 5
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


def test_for(path: str) -> str:
    if path.startswith("app/src/main/java/") and path.endswith(".kt"):
        return f"app/src/test/java/{path.removeprefix('app/src/main/java/').removesuffix('.kt')}Test.kt"
    tools = re.match(r"^(tools/(?:.+/)?)(?!test_)([^/]+\.py)$", path)
    return f"{tools.group(1)}test_{tools.group(2)}" if tools else ""


def called_names(diff: str, defined: list) -> list:
    added = "\n".join(row for row in diff.splitlines() if row.startswith("+") and not row.startswith("+++"))
    return sorted(set(CALL.findall(added)) - COMMON - set(defined))[:MAX_CALLEES]


def first_definition(grep: str, name: str, skip: set):
    definition = re.compile(rf"\b(?:fun|def)\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?{re.escape(name)}\b")
    rows = (row.split(":", 2) for row in grep.splitlines() if row.count(":") >= 2)
    found = next(((path, int(line)) for path, line, code in rows if path not in skip and definition.search(code)), None)
    return found


def snippet(path: str, text: str, line: int) -> str:
    rows = text.splitlines()[line - 1 : line - 1 + SNIPPET]
    numbered = "\n".join(f"{n:>5}  {row}" for n, row in enumerate(rows, line))
    return f"### {path}:{line}\n\n{FENCE}\n{numbered}\n{FENCE}\n"


def history_section(logs: dict) -> str:
    blocks = [f"### {path}\n\n{FENCE}\n{log.rstrip()}\n{FENCE}\n" for path, log in logs.items() if log.strip()]
    return "\n".join(blocks) or "Истории нет: файлы новые.\n"


def calls_section(calls: dict) -> str:
    blocks = [f"### {name}\n\n{FENCE}\n" + "\n".join(rows) + f"\n{FENCE}\n" for name, rows in calls.items() if rows]
    return "\n".join(blocks) or "Мест вызова вне изменённых файлов нет.\n"


def file_section(path: str, text: str) -> str:
    size = len(text.splitlines())
    if size > MAX_LINES:
        return f"### {path}\n\n{size} строк, целиком не приложен: смотри дифф.\n"
    numbered = "\n".join(f"{n:>5}  {row}" for n, row in enumerate(text.splitlines(), 1))
    return f"### {path} (номер строки слева)\n\n{FENCE}\n{numbered}\n{FENCE}\n"


def context(description: str, diff: str, files: dict, calls: dict, extras: tuple = ()) -> str:
    sections = "\n".join(file_section(path, text) for path, text in files.items())
    return "\n\n".join(
        [
            f"# Описание PR\n\n{description.rstrip()}",
            f"# Дифф\n\n{FENCE}diff\n{diff.rstrip()}\n{FENCE}",
            f"# Изменённые файлы, код под тестом и тесты изменённого кода целиком\n\n{sections}",
            f"# Места вызова изменённых определений\n\n{calls_section(calls)}",
            *extras,
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


def callee_snippets(diff: str, files: dict) -> str:
    found = (first_definition(grep_word(n), n, set(files)) for n in called_names(diff, changed_names(diff)))
    blocks = [snippet(path, Path(path).read_text(encoding="utf-8"), line) for path, line in filter(None, found)]
    return "\n".join(blocks) or "Вызываемых определений вне приложенных файлов нет.\n"


def history(base: str, paths: list) -> dict:
    return {p: git("log", f"-{HISTORY}", "--format=%h %ad %s", "--date=short", base, "--", p) for p in paths}


def main(base: str, description_path: str) -> int:
    diff = git("diff", f"{base}...HEAD")
    numstat = git("diff", "--numstat", "--no-renames", f"{base}...HEAD")
    changed = text_files(numstat)
    related = [f(path) for path in changed for f in (tested_source, test_for)]
    present = [path for path in [*DOCS, *changed, *related] if path and Path(path).is_file()]
    files = {path: Path(path).read_text(encoding="utf-8") for path in dict.fromkeys(present)}
    calls = {name: call_lines(grep_word(name), list(files)) for name in changed_names(diff)}
    extras = (
        f"# Вызываемое изменённым кодом\n\n{callee_snippets(diff, files)}",
        f"# История изменённых файлов\n\n{history_section(history(base, changed))}",
    )
    description = Path(description_path).read_text(encoding="utf-8")
    Path(".ai-review-pr.md").write_text(context(description, diff, files, calls, extras), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
