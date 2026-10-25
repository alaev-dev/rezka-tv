import json
import os
import re
import subprocess
import sys

REVIEWER = "github-actions"
REVIEWER_APP = "github-actions[bot]"
VERDICT_COUNT = re.compile(r"блокирующих нарушений: (\d+)")
BLOCKING = "**Блокирует.** "
THREADS = """query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) { pullRequest(number: $number) { reviewThreads(first: 100) {
    nodes { id isResolved path line comments(first: 1) { nodes { author { login } body } } } } } } }"""
OUTDATED = "<details><summary>Устаревшее ИИ-ревью</summary>"
RESOLVE = "mutation($id: ID!) { resolveReviewThread(input: {threadId: $id}) { thread { id } } }"


def blocking_count(verdict: dict, still_open: list = ()) -> int:
    return sum(1 for f in verdict["findings"] if f["blocking"]) + sum(1 for t in still_open if t["blocking"])


def previous_threads(response: dict) -> list:
    nodes = response["data"]["repository"]["pullRequest"]["reviewThreads"]["nodes"]
    own = [n for n in nodes if not n["isResolved"] and n["comments"]["nodes"][0]["author"]["login"] == REVIEWER]
    return [thread_summary(n) for n in own]


def thread_summary(node: dict) -> dict:
    body = node["comments"]["nodes"][0]["body"]
    return {
        "id": node["id"],
        "path": node["path"],
        "line": node["line"],
        "blocking": body.startswith(BLOCKING),
        "body": body,
    }


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


def diff_marker(diff_id: str) -> str:
    return f"<!-- ai-review-diff: {diff_id} -->"


def prior_blocking(reviews: list, diff_id: str):
    marker = diff_marker(diff_id)
    same = [r["body"] for r in reviews if r["user"]["login"] == REVIEWER_APP and marker in r["body"]]
    found = VERDICT_COUNT.search(same[-1]) if same else None
    return int(found.group(1)) if found else None


def review_payload(verdict: dict, inline: bool, previous: list = (), still_open: list = (), diff_id: str = "") -> dict:
    header = f"## ИИ-ревью: блокирующих нарушений: {blocking_count(verdict, still_open)}\n\n{verdict['summary']}"
    if previous:
        header += f"\n\nПрошлые замечания: исправлено {len(previous) - len(still_open)}, открыто {len(still_open)}."
    if diff_id:
        header += f"\n\n{diff_marker(diff_id)}"
    if inline:
        comments = [inline_comment(f) for f in verdict["findings"]]
        return {"event": "COMMENT", "body": header, "comments": comments}
    listed = "\n".join(listed_finding(f) for f in verdict["findings"])
    return {"event": "COMMENT", "body": f"{header}\n\n{listed}".rstrip()}


def collapsed(body: str) -> str:
    return f"{OUTDATED}\n\n{body}\n\n</details>"


def own_report(reviews: list):
    own = [r for r in reviews if r["user"]["login"] == REVIEWER_APP and VERDICT_COUNT.search(r["body"])]
    reports = [r for r in own if not r["body"].startswith(OUTDATED)]
    return reports[-1] if reports else None


def report_body(verdict: dict, previous: list, still_open: list, diff_id: str, unplaced: list) -> str:
    header = review_payload(verdict, True, previous, still_open, diff_id)["body"]
    listed = "\n".join(listed_finding(f) for f in unplaced)
    return f"{header}\n\n{listed}".rstrip()


def outdated_reviews(reviews: list, newest: int) -> list:
    own = (r for r in reviews if r["user"]["login"] == REVIEWER_APP and r["id"] != newest)
    return [r for r in own if r["body"] and not r["body"].startswith(OUTDATED)]


def post(repo: str, number: str, payload: dict) -> int:
    command = ["gh", "api", f"repos/{repo}/pulls/{number}/reviews", "--input", "-"]
    result = subprocess.run(command, input=json.dumps(payload), text=True, check=True, capture_output=True)
    return json.loads(result.stdout)["id"]


def post_line(repo: str, number: str, commit: str, finding: dict) -> bool:
    command = ["gh", "api", f"repos/{repo}/pulls/{number}/comments", "--input", "-"]
    payload = json.dumps({**inline_comment(finding), "commit_id": commit})
    return subprocess.run(command, check=False, input=payload, text=True, capture_output=True).returncode == 0


def edit_review(repo: str, number: str, review_id: int, body: str) -> None:
    command = ["gh", "api", "-X", "PUT", f"repos/{repo}/pulls/{number}/reviews/{review_id}", "-f", f"body={body}"]
    subprocess.run(command, text=True, check=True, capture_output=True)


def head_commit(repo: str, number: str) -> str:
    command = ["gh", "api", f"repos/{repo}/pulls/{number}", "--jq", ".head.sha"]
    return subprocess.run(command, text=True, check=True, capture_output=True).stdout.strip()


def first_report(repo: str, number: str, verdict: dict, previous: list, still_open: list, diff_id: str) -> int:
    try:
        return post(repo, number, review_payload(verdict, True, previous, still_open, diff_id))
    except subprocess.CalledProcessError:
        print("::warning title=ИИ-ревью::замечания вне строк диффа, публикую списком")
        return post(repo, number, review_payload(verdict, False, previous, still_open, diff_id))


def update_report(
    repo: str, number: str, report: dict, verdict: dict, previous: list, still_open: list, diff_id: str
) -> int:
    commit = head_commit(repo, number)
    unplaced = [f for f in verdict["findings"] if not post_line(repo, number, commit, f)]
    edit_review(repo, number, report["id"], report_body(verdict, previous, still_open, diff_id, unplaced))
    return report["id"]


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


def publish(repo: str, number: str, verdict: dict, previous: list, still_open: list, diff_id: str) -> None:
    open_ids = {t["id"] for t in still_open}
    for fixed in (t for t in previous if t["id"] not in open_ids):
        graphql(RESOLVE, id=fixed["id"])
    reviews = fetch_reviews(repo, number)
    report = own_report(reviews)
    if report is None:
        current = first_report(repo, number, verdict, previous, still_open, diff_id)
    else:
        current = update_report(repo, number, report, verdict, previous, still_open, diff_id)
    for old in outdated_reviews(reviews, current):
        edit_review(repo, number, old["id"], collapsed(old["body"]))


def fetch_reviews(repo: str, number: str) -> list:
    command = ["gh", "api", "--paginate", "--slurp", f"repos/{repo}/pulls/{number}/reviews"]
    pages = json.loads(subprocess.run(command, text=True, check=True, capture_output=True).stdout)
    return [review for page in pages for review in page]


def main(mode: str) -> int:
    repo, number = os.environ["REPO"], os.environ["PR_NUMBER"]
    diff_id = os.environ.get("DIFF_ID", "")
    if mode == "prior":
        blocking = prior_blocking(fetch_reviews(repo, number), diff_id)
        print("" if blocking is None else blocking)
        return 0
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
    publish(repo, number, verdict, previous, still_open, diff_id)
    print(verdict["summary"])
    return 1 if blocking_count(verdict, still_open) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "publish"))
