import json
import os
import subprocess
import sys

REVIEWER = "github-actions"
BLOCKING = "**Блокирует.** "
THREADS = """query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) { pullRequest(number: $number) { reviewThreads(first: 100) {
    nodes { id isResolved path line comments(first: 1) { nodes { author { login } body } } } } } } }"""
RESOLVE = "mutation($id: ID!) { resolveReviewThread(input: {threadId: $id}) { thread { id } } }"


def blocking_count(verdict: dict, still_open: list = ()) -> int:
    return sum(1 for f in verdict["findings"] if f["blocking"]) + sum(1 for t in still_open if t["blocking"])


def previous_threads(response: dict) -> list:
    nodes = response["data"]["repository"]["pullRequest"]["reviewThreads"]["nodes"]
    own = [n for n in nodes if not n["isResolved"] and n["comments"]["nodes"][0]["author"]["login"] == REVIEWER]
    return [thread_summary(n) for n in own]


def thread_summary(node: dict) -> dict:
    body = node["comments"]["nodes"][0]["body"]
    return {"id": node["id"], "path": node["path"], "line": node["line"], "blocking": body.startswith(BLOCKING), "body": body}


def open_previous(previous: list, verdict: dict) -> list:
    fixed = {p["id"] for p in verdict["previous"] if p["fixed"]}
    return [t for t in previous if t["id"] not in fixed]


def comment_body(finding: dict) -> str:
    prefix = BLOCKING if finding["blocking"] else ""
    return prefix + finding["message"]


def inline_comment(finding: dict) -> dict:
    return {"path": finding["path"], "line": finding["line"], "side": "RIGHT", "body": comment_body(finding)}


def listed_finding(finding: dict) -> str:
    return f"- `{finding['path']}:{finding['line']}` — {comment_body(finding)}"


def review_payload(verdict: dict, inline: bool, previous: list = (), still_open: list = ()) -> dict:
    header = f"## ИИ-ревью: блокирующих нарушений: {blocking_count(verdict, still_open)}\n\n{verdict['summary']}"
    if previous:
        header += f"\n\nПрошлые замечания: исправлено {len(previous) - len(still_open)}, открыто {len(still_open)}."
    if inline:
        comments = [inline_comment(f) for f in verdict["findings"]]
        return {"event": "COMMENT", "body": header, "comments": comments}
    listed = "\n".join(listed_finding(f) for f in verdict["findings"])
    return {"event": "COMMENT", "body": f"{header}\n\n{listed}".rstrip()}


def post(repo: str, number: str, payload: dict) -> None:
    command = ["gh", "api", f"repos/{repo}/pulls/{number}/reviews", "--input", "-"]
    subprocess.run(command, input=json.dumps(payload), text=True, check=True, capture_output=True)


def graphql(query: str, **variables: str) -> dict:
    fields = [arg for name, value in variables.items() for arg in ("-F", f"{name}={value}")]
    command = ["gh", "api", "graphql", "-f", f"query={query}", *fields]
    result = subprocess.run(command, check=False, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"gh api graphql: {result.stderr.strip() or result.stdout.strip()}")
    return json.loads(result.stdout)


def fetch_previous(repo: str, number: str) -> list:
    owner, name = repo.split("/")
    return previous_threads(graphql(THREADS, owner=owner, name=name, number=number))


def publish(repo: str, number: str, verdict: dict, previous: list, still_open: list) -> None:
    open_ids = {t["id"] for t in still_open}
    for fixed in (t for t in previous if t["id"] not in open_ids):
        graphql(RESOLVE, id=fixed["id"])
    try:
        post(repo, number, review_payload(verdict, True, previous, still_open))
    except subprocess.CalledProcessError:
        print("::warning title=ИИ-ревью::замечания вне строк диффа, публикую списком")
        post(repo, number, review_payload(verdict, False, previous, still_open))


def main(mode: str) -> int:
    repo, number = os.environ["REPO"], os.environ["PR_NUMBER"]
    previous = fetch_previous(repo, number)
    if mode == "previous":
        print(json.dumps(previous, ensure_ascii=False, indent=2))
        return 0
    raw = os.environ.get("VERDICT", "")
    if not raw:
        print("::error title=ИИ-ревью::агент не вернул вердикт, смотрите лог шага ревью")
        return 1
    verdict = json.loads(raw)
    still_open = open_previous(previous, verdict)
    publish(repo, number, verdict, previous, still_open)
    print(verdict["summary"])
    return 1 if blocking_count(verdict, still_open) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "publish"))
