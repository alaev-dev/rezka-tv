import re
import subprocess
import sys

KOTLIN_TEST = re.compile(r"@Test\b")
PYTHON_TEST = re.compile(r"^\s*def test_", re.MULTILINE)
CODE = re.compile(r"^(app/src/main/.+\.kt|tools/(?!.*test_)[^/].*\.py)$")
TESTS = re.compile(r"^(app/src/test/.+\.kt|tools/.*test_[^/]*\.py)$")
SPACE = re.compile(r"\s+")


def test_count(path: str, text: str) -> int:
    pattern = KOTLIN_TEST if path.endswith(".kt") else PYTHON_TEST
    return len(pattern.findall(text)) if TESTS.match(path) else 0


def lost_tests(before: dict, after: dict) -> list:
    old, new = sum(test_count(p, t) for p, t in before.items()), sum(test_count(p, t) for p, t in after.items())
    return [] if new >= old else [f"тестов стало меньше: было {old}, стало {new}; тесты не удаляют, а исправляют"]


def reformatted_only(before: str, after: str) -> bool:
    return SPACE.sub("", before) == SPACE.sub("", after)


def untested_change(before: dict, after: dict) -> list:
    changed = sorted(set(before) | set(after))
    meaningful = [p for p in changed if not reformatted_only(before.get(p, ""), after.get(p, ""))]
    code, tests = [p for p in meaningful if CODE.match(p)], [p for p in meaningful if TESTS.match(p)]
    return [f"код меняется без тестов: {', '.join(code)}"] if code and not tests else []


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def contents(ref: str, paths: list) -> dict:
    tracked = set(git("ls-tree", "-r", "--name-only", ref).splitlines())
    return {path: git("show", f"{ref}:{path}") for path in paths if path in tracked}


def main(base: str) -> int:
    since = git("merge-base", base, "HEAD").strip()
    changed = git("diff", "--name-only", "--no-renames", since, "HEAD").splitlines()
    before, after = contents(since, changed), contents("HEAD", changed)
    problems = lost_tests(before, after) + untested_change(before, after)
    print("\n".join(f"::error title=Тесты в PR::{problem}" for problem in problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "origin/main"))
