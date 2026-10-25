import json
import os
import sys
import urllib.request
from pathlib import Path

URL = "https://api.deepseek.com/chat/completions"
TIMEOUT = 600
ANSWER = (
    "Ответь одним JSON-объектом без текста вокруг: "
    '{"summary": строка, "findings": [{"path": строка, "line": целое ≥ 1, "blocking": bool, "message": строка}], '
    '"previous": [{"id": строка, "fixed": bool}]}.'
)
FIELDS = {"summary": str, "findings": list, "previous": list}
FINDING = {"path": str, "line": int, "blocking": bool, "message": str}
PREVIOUS = {"id": str, "fixed": bool}
INPUTS = (".ai-review-rules.md", ".ai-review-pr.md", ".ai-review-previous.json")


def messages(rules: str, context: str, previous: str) -> list:
    task = f"{context}\n\n# Прошлые незакрытые замечания\n\n{previous.strip() or '[]'}"
    return [{"role": "system", "content": f"{rules.rstrip()}\n\n{ANSWER}"}, {"role": "user", "content": task}]


def payload(model: str, chat: list) -> dict:
    return {"model": model, "messages": chat, "response_format": {"type": "json_object"}}


def shaped(item: dict, shape: dict) -> bool:
    return isinstance(item, dict) and all(isinstance(item.get(key), kind) for key, kind in shape.items())


def schema_problems(data: dict) -> list:
    problems = [f"поле {key}" for key, kind in FIELDS.items() if not isinstance(data.get(key), kind)]
    if problems:
        return problems
    bad = [f for f in data["findings"] if not shaped(f, FINDING) or isinstance(f["line"], bool) or f["line"] < 1]
    return [f"замечание {f}" for f in bad] + [f"прошлое {p}" for p in data["previous"] if not shaped(p, PREVIOUS)]


def verdict(answer: str) -> dict:
    data = json.loads(answer)
    problems = schema_problems(data) if isinstance(data, dict) else ["не объект"]
    if problems:
        raise ValueError(f"ответ не по схеме: {'; '.join(problems)}"[:2000])
    return data


def ask(key: str, body: dict) -> dict:
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    request = urllib.request.Request(URL, json.dumps(body).encode(), headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return json.loads(response.read())


def write_verdict(answer: str, out: str = "") -> None:
    text = json.dumps(verdict(answer), ensure_ascii=False)
    if out:
        Path(out).write_text(text, encoding="utf-8")
    else:
        print(text)


def output_path(argv: list) -> str:
    return argv[1] if len(argv) > 1 else ""


def run(out: str, inputs: tuple = INPUTS, call=ask, env=os.environ) -> int:
    if out:
        Path(out).unlink(missing_ok=True)
    rules, context, previous = (Path(p).read_text(encoding="utf-8") for p in inputs)
    response = call(env["DEEPSEEK_API_KEY"], payload(env["MODEL"], messages(rules, context, previous)))
    print(f"токены: {json.dumps(response.get('usage', {}))}", file=sys.stderr)
    write_verdict(response["choices"][0]["message"]["content"], out)
    return 0


if __name__ == "__main__":
    sys.exit(run(output_path(sys.argv)))
