import re
import subprocess
import sys

from gates import comments

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


def cosmetic_only(path: str, before: str, after: str) -> bool:
    return SPACE.sub("", comments.uncommented(path, before)) == SPACE.sub("", comments.uncommented(path, after))


def untested_change(before: dict, after: dict) -> list:
    changed = sorted(set(before) | set(after))
    meaningful = [p for p in changed if not cosmetic_only(p, before.get(p, ""), after.get(p, ""))]
    code, tests = [p for p in meaningful if CODE.match(p)], [p for p in meaningful if TESTS.match(p)]
    return [f"код меняется без тестов: {', '.join(code)}"] if code and not tests else []


def relevant(paths: list) -> list:
    return [path for path in paths if CODE.match(path) or TESTS.match(path)]


def git(repo: str, *args: str) -> str:
    return subprocess.run(["git", "-C", repo, *args], check=True, capture_output=True, text=True).stdout


def contents(repo: str, ref: str, paths: list) -> dict:
    tracked = set(git(repo, "ls-tree", "-r", "--name-only", ref).splitlines())
    return {path: git(repo, "show", f"{ref}:{path}") for path in paths if path in tracked}


def problems(base: str, repo: str = ".") -> list:
    since = git(repo, "merge-base", base, "HEAD").strip()
    changed = relevant(git(repo, "diff", "--name-only", "--no-renames", since, "HEAD").splitlines())
    before, after = contents(repo, since, changed), contents(repo, "HEAD", changed)
    return lost_tests(before, after) + untested_change(before, after)


def main(base: str) -> int:
    found = problems(base)
    print("\n".join(f"::error title=Тесты в PR::{problem}" for problem in found))
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "origin/main"))
