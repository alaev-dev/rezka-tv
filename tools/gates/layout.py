import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

LEDGER = "quality/layout-baseline.txt"
SOURCE_ROOT = re.compile(r"^(?P<module>.+?/src/(?P<set>[^/]+)/java)/(?P<package>.+)/[^/]+\.kt$")
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
MODIFIERS = r"(?:(?:public|internal|private|data|sealed|enum|abstract|open|value|annotation|fun|expect|actual)\s+)*"
TOP_LEVEL_TYPE = re.compile(rf"^{MODIFIERS}(?:class|object|interface)\s+(\w+)", re.MULTILINE)
TEST_SUFFIX = "Test"


def top_level_types(text: str) -> list:
    return TOP_LEVEL_TYPE.findall(text)


def declared_package(text: str) -> str:
    match = PACKAGE.search(text)
    return match.group(1) if match else ""


def package_problems(path: str, text: str) -> list:
    root = SOURCE_ROOT.match(path)
    expected = root.group("package").replace("/", ".") if root else ""
    found = declared_package(text)
    return [] if found == expected else [f"пакет {found or '(нет)'} не совпадает с каталогом {expected}"]


def type_problems(path: str, text: str) -> list:
    types = top_level_types(text)
    stem = PurePosixPath(path).stem
    if len(types) > 1:
        return [f"несколько верхнеуровневых классов ({', '.join(types)}): по одному на файл"]
    if types and types[0] != stem:
        return [f"класс {types[0]} лежит в {stem}.kt: имя файла должно совпадать с именем класса"]
    return []


def mirrored_source(path: str) -> str:
    root = SOURCE_ROOT.match(path)
    stem = PurePosixPath(path).stem
    if not root or root.group("set") != "test" or not stem.endswith(TEST_SUFFIX):
        return ""
    module = root.group("module").removesuffix("/test/java")
    return f"{module}/main/java/{root.group('package')}/{stem.removesuffix(TEST_SUFFIX)}.kt"


def mirror_problems(path: str, existing: set) -> list:
    source = mirrored_source(path)
    return [f"тест без исходника: нет {source}"] if source and source not in existing else []


def problems(sources: dict) -> dict:
    existing = set(sources)
    found = {
        path: package_problems(path, text) + type_problems(path, text) + mirror_problems(path, existing)
        for path, text in sources.items()
        if SOURCE_ROOT.match(path)
    }
    return {path: messages for path, messages in found.items() if messages}


def violations(found: dict, ledger: set) -> list:
    fresh = [(path, message) for path, messages in sorted(found.items()) if path not in ledger for message in messages]
    stale = [(path, f"раскладка в порядке: уберите файл из {LEDGER}") for path in sorted(ledger) if path not in found]
    return fresh + stale


def parse_ledger(text: str) -> set:
    return {row.strip() for row in text.splitlines() if row.strip()}


def annotation(path: str, message: str) -> str:
    return f"::error file={path},line=1,title=Раскладка файлов::{message}"


def kotlin_sources() -> dict:
    output = subprocess.run(["git", "ls-files", "-z", "*.kt"], check=True, capture_output=True, text=True).stdout
    paths = [path for path in output.split("\0") if path and Path(path).is_file()]
    return {path: Path(path).read_text(encoding="utf-8", errors="replace") for path in paths}


def main() -> int:
    ledger = parse_ledger(Path(LEDGER).read_text(encoding="utf-8")) if Path(LEDGER).exists() else set()
    found = violations(problems(kotlin_sources()), ledger)
    for violation in found:
        print(annotation(*violation))
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
