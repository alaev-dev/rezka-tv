import re
import subprocess
import sys
from pathlib import Path

LEDGER = "quality/tests-baseline.txt"
MAX_LINES = 12
FILE_SCOPE = "(файл)"
TEST_FUN = re.compile(r"@Test\s+fun\s+(`[^`]+`|\w+)\s*\([^)]*\)\s*(?::\s*\w+\s*)?([{=])")
ASSERT = re.compile(r"\b(assert\w*|fail)\s*\(")
LITERAL = re.compile(r"\"(\\.|[^\"\\])*\"|'(\\.|[^'\\])'|\b\d[\d_]*(\.\d+)?[LlFfDd]?\b|\b(true|false)\b")
FIXTURE = re.compile(r"@(Before|After)(Each|All|Class)?\b")
FIELD = re.compile(r"^( {4})?(private |internal )?(const |lateinit )?(val|var) |^\s*companion object\b", re.MULTILINE)
WORD = re.compile(r"[A-Z]?[a-z0-9]+")


def closing(text: str, start: int, opening: str, close: str) -> int:
    depth, i = 0, start
    while i < len(text):
        if text[i] == '"':
            i = text.index('"', i + 1) if '"' in text[i + 1 :] else len(text)
        elif text[i] == opening:
            depth += 1
        elif text[i] == close:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text)


def test_bodies(text: str) -> list:
    found = []
    for match in TEST_FUN.finditer(text):
        name, kind = match.group(1).strip("`"), match.group(2)
        start = match.end() - 1
        end = closing(text, start, "{", "}") if kind == "{" else text.find("\n\n", start)
        found.append((name, text[start + 1 : end if end > 0 else len(text)]))
    return found


def name_problems(name: str) -> list:
    words = WORD.findall(name)
    sentence = len(words) >= 3 and words[0] != "test" and words[0].endswith("s")
    return [] if sentence else [f"имя {name}: предложение о поведении с глагола, например rejectsExpiredCode"]


def assert_problems(body: str) -> list:
    calls = list(ASSERT.finditer(body))
    if len(calls) != 1:
        return [f"assert в тесте: {len(calls)}, нужен ровно один"]
    call = calls[0]
    end = closing(body, call.end() - 1, "(", ")")
    args = body[call.end() : end]
    message = re.match(r"\s*\"(\\.|[^\"\\])*\"\s*,", args)
    problems = [] if not body[end + 1 :].strip() else ["assert должен быть последним"]
    if call.group(1) != "assertThat" or not message:
        return problems + ['нужен assertThat("сообщение", фактическое, матчер)']
    rest = args[message.end() :]
    return problems + (["литерал в assert: вынесите значение в переменную"] if LITERAL.search(rest) else [])


def body_problems(name: str, body: str) -> list:
    lines = [row for row in body.splitlines() if row.strip()]
    size = [] if len(lines) <= MAX_LINES else [f"тест длиннее {MAX_LINES} строк: {len(lines)}"]
    return name_problems(name) + size + assert_problems(body)


def file_problems(text: str) -> list:
    fixtures = ["@Before/@After запрещены: готовьте данные в самом тесте"] if FIXTURE.search(text) else []
    fields = ["константы и поля в тестах запрещены: значения — в самом тесте"] if FIELD.search(text) else []
    return fixtures + fields


def problems(path: str, text: str) -> dict:
    found = {f"{path}::{name}": body_problems(name, body) for name, body in test_bodies(text)}
    found[f"{path}::{FILE_SCOPE}"] = file_problems(text)
    return {key: messages for key, messages in found.items() if messages}


def violations(found: dict, ledger: set) -> list:
    fresh = [(key, message) for key, messages in sorted(found.items()) if key not in ledger for message in messages]
    stale = [(key, f"тест в порядке: уберите {key} из {LEDGER}") for key in sorted(ledger) if key not in found]
    return fresh + stale


def parse_ledger(text: str) -> set:
    return {row.strip() for row in text.splitlines() if row.strip()}


def annotation(key: str, message: str) -> str:
    return f"::error file={key.split('::')[0]},line=1,title=Правила тестов::{key.split('::')[1]}: {message}"


def kotlin_tests() -> list:
    command = ["git", "ls-files", "-z", "app/src/test/*.kt"]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    return [path for path in output.split("\0") if path and Path(path).is_file()]


def main() -> int:
    ledger = parse_ledger(Path(LEDGER).read_text(encoding="utf-8")) if Path(LEDGER).exists() else set()
    found = {k: m for p in kotlin_tests() for k, m in problems(p, Path(p).read_text(encoding="utf-8")).items()}
    reported = violations(found, ledger)
    print("\n".join(annotation(*violation) for violation in reported))
    return 1 if reported else 0


if __name__ == "__main__":
    sys.exit(main())
