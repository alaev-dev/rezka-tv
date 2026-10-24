import ast
import re
import subprocess
import sys
from pathlib import Path

LEDGER = "quality/tests-baseline.txt"
MAX_LINES = 12
TEST_FUN = re.compile(
    r"@Test(?:\([^)]*\))?(?:\s*@\w+(?:\([^)]*\))?)*\s+fun\s+(`[^`]+`|\w+)\s*\([^)]*\)\s*(?::\s*\w+\s*)?([{=])"
)
ASSERT = re.compile(r"\b(assert\w*|fail)\s*\(")
LITERAL = re.compile(r"\"(\\.|[^\"\\])*\"|'(\\.|[^'\\])'|\b\d[\d_]*(\.\d+)?[LlFfDd]?\b|\b(true|false)\b")
FIXTURE = re.compile(r"@(Before|After)(Each|All|Class)?\b")
FIELD = re.compile(r"^( {4})?(private |internal )?(const |lateinit )?(val|var) |^\s*companion object\b", re.MULTILINE)
TOKEN = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\])\'|[(){}]')
WORD = re.compile(r"[A-Z]?[a-z0-9]+")


def closing(text: str, start: int, opening: str, close: str) -> int:
    depth = 0
    for token in TOKEN.finditer(text, start):
        depth += {opening: 1, close: -1}.get(token.group(), 0)
        if depth == 0:
            return token.start()
    return len(text)


def test_bodies(text: str) -> list:
    found = []
    for match in TEST_FUN.finditer(text):
        name, kind = match.group(1).strip("`"), match.group(2)
        start = match.end() - 1
        end = closing(text, start, "{", "}") if kind == "{" else text.find("\n\n", start)
        found.append((name, text[start + 1 : end if end > 0 else len(text)]))
    return found


def name_problems(name: str, words: list) -> list:
    sentence = len(words) >= 3 and words[0] != "test" and words[0].endswith("s")
    return [] if sentence else [f"имя {name}: предложение о поведении с глагола, например rejectsExpiredCode"]


def assert_problems(body: str) -> list:
    calls = list(ASSERT.finditer(body))
    if len(calls) != 1:
        return [f"assert в тесте: {len(calls)}, нужен ровно один"]
    call, end = calls[0], closing(body, calls[0].end() - 1, "(", ")")
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
    return name_problems(name, WORD.findall(name)) + size + assert_problems(body)


def file_problems(text: str) -> list:
    fixtures = ["@Before/@After запрещены: готовьте данные в самом тесте"] if FIXTURE.search(text) else []
    fields = ["константы и поля в тестах запрещены: значения — в самом тесте"] if FIELD.search(text) else []
    return fixtures + fields


def problems(path: str, text: str) -> dict:
    found = {f"{path}::{name}": body_problems(name, body) for name, body in test_bodies(text)}
    found[f"{path}::(файл)"] = file_problems(text)
    return {key: messages for key, messages in found.items() if messages}


def is_literal(node: ast.AST) -> bool:
    parts = getattr(node, "elts", None) or [*getattr(node, "keys", []), *getattr(node, "values", [])]
    containers = (ast.List, ast.Tuple, ast.Set, ast.Dict)
    return isinstance(node, ast.Constant) or (isinstance(node, containers) and all(map(is_literal, parts)))


def python_assert_problems(test: ast.FunctionDef) -> list:
    calls = [n for n in ast.walk(test) if isinstance(n, ast.Call) and getattr(n.func, "attr", "").startswith("assert")]
    if len(calls) != 1:
        return [f"assert в тесте: {len(calls)}, нужен ровно один"]
    call, last = calls[0], test.body[-1]
    problems = [] if isinstance(last, ast.Expr) and last.value is call else ["assert должен быть последним"]
    keyword = [k.value for k in call.keywords if k.arg == "msg"]
    message = keyword or call.args[-1:]
    if not message or not isinstance(message[0], (ast.Constant, ast.JoinedStr)):
        return problems + ["нужно сообщение последним аргументом assert"]
    checked = call.args if keyword else call.args[:-1]
    return problems + (["литерал в assert: вынесите значение в переменную"] if any(map(is_literal, checked)) else [])


def python_test_problems(test: ast.FunctionDef) -> list:
    size = test.end_lineno - test.lineno
    long = [] if size <= MAX_LINES else [f"тест длиннее {MAX_LINES} строк: {size}"]
    words = test.name.removeprefix("test_").split("_")
    return name_problems(test.name, words) + long + python_assert_problems(test)


def python_file_problems(tree: ast.Module) -> list:
    classes = [n for n in tree.body if isinstance(n, ast.ClassDef)]
    members = [m for c in classes for m in c.body]
    fixtures = [m for m in members if getattr(m, "name", "") in ("setUp", "tearDown", "setUpClass", "tearDownClass")]
    fields = [n for n in [*tree.body, *members] if isinstance(n, (ast.Assign, ast.AnnAssign))]
    return (["setUp/tearDown запрещены: готовьте данные в самом тесте"] if fixtures else []) + (
        ["константы и поля в тестах запрещены: значения — в самом тесте"] if fields else []
    )


def python_problems(path: str, text: str) -> dict:
    tree = ast.parse(text)
    tests = [(c.name, m) for c in tree.body if isinstance(c, ast.ClassDef) for m in c.body]
    found = {f"{path}::{c}.{m.name}": python_test_problems(m) for c, m in tests if m.name.startswith("test_")}
    found[f"{path}::(файл)"] = python_file_problems(tree)
    return {key: messages for key, messages in found.items() if messages}


def violations(found: dict, ledger: set) -> list:
    fresh = [(key, message) for key, messages in sorted(found.items()) if key not in ledger for message in messages]
    stale = [(key, f"тест в порядке: уберите {key} из {LEDGER}") for key in sorted(ledger) if key not in found]
    return fresh + stale


def parse_ledger(text: str) -> set:
    return {row.strip() for row in text.splitlines() if row.strip()}


def annotation(key: str, message: str) -> str:
    return f"::error file={key.split('::')[0]},line=1,title=Правила тестов::{key.split('::')[1]}: {message}"


def test_files() -> list:
    command = ["git", "ls-files", "-z", "app/src/test/*.kt", "tools/*test_*.py"]
    output = subprocess.run(command, check=True, capture_output=True, text=True).stdout
    return [path for path in output.split("\0") if path and Path(path).is_file()]


def main() -> int:
    ledger = parse_ledger(Path(LEDGER).read_text(encoding="utf-8")) if Path(LEDGER).exists() else set()
    check = {".kt": problems, ".py": python_problems}
    found = {k: m for p in test_files() for k, m in check[Path(p).suffix](p, Path(p).read_text("utf-8")).items()}
    reported = violations(found, ledger)
    print("\n".join(annotation(*violation) for violation in reported))
    return 1 if reported else 0


if __name__ == "__main__":
    sys.exit(main())
