import subprocess
import sys
from pathlib import Path

ALWAYS_DENIED = ("WebFetch", "WebSearch")
GUARDED = (
    "tools/",
    ".github/",
    "gradle/",
    "README.md",
    "THIRD_PARTY.md",
    "detekt.yml",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "settings.gradle.kts",
)
MAX_LINES = 600
FENCE = "`````"


def touched(guarded: str, changed: list) -> bool:
    return any(p.startswith(guarded) for p in changed) if guarded.endswith("/") else guarded in changed


def deny_rules(changed: list) -> list:
    guarded = [g for g in GUARDED if not touched(g, changed)]
    return [*ALWAYS_DENIED, *(f"Read(./{g}**)" if g.endswith("/") else f"Read(./{g})" for g in guarded)]


def file_section(path: str, text: str) -> str:
    size = len(text.splitlines())
    if size > MAX_LINES:
        return f"### {path}\n\n{size} строк, целиком не приложен: читай нужные места сам.\n"
    return f"### {path}\n\n{FENCE}\n{text.rstrip()}\n{FENCE}\n"


def context(description: str, diff: str, files: dict) -> str:
    sections = "\n".join(file_section(path, text) for path, text in files.items())
    return (
        f"{description.rstrip()}\n\n{FENCE}diff\n{diff.rstrip()}\n{FENCE}\n\n## Изменённые файлы целиком\n\n{sections}"
    )


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def text_files(numstat: str) -> list:
    rows = [line.split("\t") for line in numstat.splitlines()]
    return [path for added, _, path in rows if added != "-"]


def main(base: str, description_path: str) -> int:
    changed = git("diff", "--name-only", "--no-renames", f"{base}...HEAD").splitlines()
    numstat = git("diff", "--numstat", "--no-renames", f"{base}...HEAD")
    present = [path for path in text_files(numstat) if Path(path).is_file()]
    files = {path: Path(path).read_text(encoding="utf-8") for path in present}
    description = Path(description_path).read_text(encoding="utf-8")
    Path(".ai-review-pr.md").write_text(context(description, git("diff", f"{base}...HEAD"), files), encoding="utf-8")
    print(",".join(deny_rules(changed)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
