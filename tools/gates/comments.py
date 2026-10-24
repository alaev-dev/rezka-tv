import re
from pathlib import Path

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


SCANNERS = {
    ".kt": c_like_comments,
    ".kts": c_like_comments,
    ".java": c_like_comments,
    ".mjs": c_like_comments,
    ".js": c_like_comments,
    ".xml": xml_comments,
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


def scan(paths: list) -> dict:
    readable = [p for p in paths if scanner_for(p) and Path(p).is_file()]
    found = {p: comment_lines(p, Path(p).read_text(encoding="utf-8", errors="replace")) for p in readable}
    return {p: lines for p, lines in found.items() if lines}
