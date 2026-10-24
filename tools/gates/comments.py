import ast
import io
import re
import subprocess
import sys
import tokenize
from pathlib import Path

LEDGER = "quality/comments-baseline.txt"
HASH = re.compile(r"(^|\s)#")
QUOTED = re.compile(r"\"(\\.|[^\"\\])*\"|'[^']*'")


def string_end(text: str, start: int, quote: str) -> int:
    i = start + 1
    while i < len(text) and text[i] not in (quote, "\n"):
        i += 2 if text[i] == "\\" else 1
    return min(i + 1, len(text))


def block_end(text: str, start: int, closing: str) -> int:
    end = text.find(closing, start)
    return len(text) if end < 0 else end + len(closing)


def c_like_comments(text: str) -> list:
    found, i, line = [], 0, 1
    while i < len(text):
        if text.startswith('"""', i):
            end = block_end(text, i + 3, '"""')
        elif text[i] in "\"'`":
            end = string_end(text, i, text[i])
        elif text.startswith("//", i):
            found.append(line)
            end = block_end(text, i, "\n") - 1
        elif text.startswith("/*", i):
            found.append(line)
            end = block_end(text, i + 2, "*/")
        else:
            end = i + 1
        line += text.count("\n", i, end)
        i = end
    return found


def xml_comments(text: str) -> list:
    return [n for n, row in enumerate(text.splitlines(), 1) if "<!--" in row]


def hash_comments(text: str) -> list:
    return [n for n, row in enumerate(text.splitlines(), 1) if HASH.search(QUOTED.sub("", row))]


class UnparsableSource(Exception):
    def __init__(self, path: str, line: int):
        super().__init__(f"{path}:{line}")
        self.path = path
        self.line = line


def is_shebang(token: tokenize.TokenInfo) -> bool:
    return token.start == (1, 0) and token.string.startswith("#!")


def python_comments(text: str) -> list:
    tokens = tokenize.generate_tokens(io.StringIO(text).readline)
    found = [t.start[0] for t in tokens if t.type == tokenize.COMMENT and not is_shebang(t)]
    return sorted(found + docstring_lines(text))


def docstring_lines(text: str) -> list:
    tree = ast.parse(text)
    scopes = (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)
    nodes = [tree, *(n for n in ast.walk(tree) if isinstance(n, scopes))]
    return [n.body[0].lineno for n in nodes if ast.get_docstring(n) is not None]


SCANNERS = {
    ".kt": c_like_comments,
    ".kts": c_like_comments,
    ".java": c_like_comments,
    ".mjs": c_like_comments,
    ".js": c_like_comments,
    ".xml": xml_comments,
    ".py": python_comments,
    ".yml": hash_comments,
    ".yaml": hash_comments,
    ".toml": hash_comments,
    ".properties": hash_comments,
    ".pro": hash_comments,
    ".gitignore": hash_comments,
}


def scanner_for(path: str):
    return SCANNERS.get(Path(path).suffix) or SCANNERS.get(Path(path).name)


def comment_lines(path: str, text: str) -> list:
    scanner = scanner_for(path)
    return scanner(text) if scanner else []


def parsed_lines(path: str) -> list:
    try:
        return comment_lines(path, Path(path).read_text(encoding="utf-8", errors="replace"))
    except (SyntaxError, tokenize.TokenError) as error:
        line = getattr(error, "lineno", None) or 1
        raise UnparsableSource(path, line) from error


def scan(paths: list) -> dict:
    readable = [p for p in paths if scanner_for(p) and Path(p).is_file()]
    found = {p: parsed_lines(p) for p in readable}
    return {p: lines for p, lines in found.items() if lines}


def parse_ledger(text: str) -> set:
    return {row.strip() for row in text.splitlines() if row.strip()}


def violations(found: dict, ledger: set, changed: set) -> list:
    fresh = [(p, n, "комментарий запрещён: удалите его") for p, lines in found.items() if p not in ledger for n in lines]
    touched = [(p, lines[0], "файл правится: удалите в нём все комментарии и уберите его из журнала")
               for p, lines in found.items() if p in ledger and p in changed]
    stale = [(p, 1, f"комментариев нет: уберите файл из {LEDGER}") for p in sorted(ledger) if p not in found]
    return sorted(fresh + touched) + stale


def annotation(path: str, line: int, message: str) -> str:
    return f"::error file={path},line={line},title=Комментарий::{message}"


def git_paths(*args: str) -> list:
    output = subprocess.run(["git", *args, "-z"], check=True, capture_output=True, text=True).stdout
    return [path for path in output.split("\0") if path]


def main(base: str) -> int:
    ledger = parse_ledger(Path(LEDGER).read_text(encoding="utf-8")) if Path(LEDGER).exists() else set()
    changed = set(git_paths("diff", "--name-only", f"{base}...HEAD"))
    try:
        found = violations(scan(git_paths("ls-files")), ledger, changed)
    except UnparsableSource as error:
        found = [(error.path, error.line, "файл Python не разбирается: исправьте синтаксис")]
    for violation in found:
        print(annotation(*violation))
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "origin/main"))
