import json
import os
import subprocess
import sys


def blocking_count(verdict: dict) -> int:
    return sum(1 for f in verdict["findings"] if f["blocking"])


def comment_body(finding: dict) -> str:
    prefix = "**Блокирует.** " if finding["blocking"] else ""
    return prefix + finding["message"]


def inline_comment(finding: dict) -> dict:
    return {"path": finding["path"], "line": finding["line"], "side": "RIGHT", "body": comment_body(finding)}


def listed_finding(finding: dict) -> str:
    return f"- `{finding['path']}:{finding['line']}` — {comment_body(finding)}"


def review_payload(verdict: dict, inline: bool) -> dict:
    header = f"## ИИ-ревью: блокирующих нарушений: {blocking_count(verdict)}\n\n{verdict['summary']}"
    if inline:
        comments = [inline_comment(f) for f in verdict["findings"]]
        return {"event": "COMMENT", "body": header, "comments": comments}
    listed = "\n".join(listed_finding(f) for f in verdict["findings"])
    return {"event": "COMMENT", "body": f"{header}\n\n{listed}".rstrip()}


def post(repo: str, number: str, payload: dict) -> None:
    command = ["gh", "api", f"repos/{repo}/pulls/{number}/reviews", "--input", "-"]
    subprocess.run(command, input=json.dumps(payload), text=True, check=True, capture_output=True)


def publish(repo: str, number: str, verdict: dict) -> None:
    try:
        post(repo, number, review_payload(verdict, True))
    except subprocess.CalledProcessError:
        print("::warning title=ИИ-ревью::замечания вне строк диффа, публикую списком")
        post(repo, number, review_payload(verdict, False))


def main() -> int:
    raw = os.environ.get("VERDICT", "")
    if not raw:
        print("::error title=ИИ-ревью::агент не вернул вердикт, смотрите лог шага ревью")
        return 1
    verdict = json.loads(raw)
    publish(os.environ["REPO"], os.environ["PR_NUMBER"], verdict)
    print(verdict["summary"])
    return 1 if blocking_count(verdict) else 0


if __name__ == "__main__":
    sys.exit(main())
