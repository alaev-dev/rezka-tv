import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from gates.ai_review_request import messages, output_path, payload, run, verdict, write_verdict


def answer(**fields) -> str:
    base = {"summary": "итог", "findings": [], "previous": []}
    return json.dumps({**base, **fields}, ensure_ascii=False)


def finding(line) -> dict:
    return {"path": "A.kt", "line": line, "blocking": True, "message": "ошибка"}


def inputs(folder: str) -> tuple:
    texts = {"rules.md": "# Правила", "pr.md": "# Дифф", "previous.json": '[{"id": "T1"}]'}
    for name, text in texts.items():
        Path(folder, name).write_text(text, encoding="utf-8")
    return tuple(str(Path(folder, name)) for name in texts)


def replying(text: str, sent: list):
    def reply(key: str, body: dict) -> dict:
        system, user = (m["content"] for m in body["messages"])
        layout = (system.startswith("# Правила"), user.startswith("# Дифф"), '"T1"' in user)
        sent.append((key, body["model"], layout))
        return {"choices": [{"message": {"content": text}}], "usage": {}}

    return reply


def replies(texts: list, chats: list):
    def reply(key: str, body: dict) -> dict:
        chats.append(body["messages"])
        return {"choices": [{"message": {"content": texts[len(chats) - 1]}}], "usage": {"total_tokens": len(chats)}}

    return reply


def failing(key: str, body: dict) -> dict:
    raise TimeoutError("нет ответа")


def env() -> dict:
    return {"DEEPSEEK_API_KEY": "k", "MODEL": "deepseek-flash"}


class AiReviewRequestTest(unittest.TestCase):
    def test_puts_rules_and_answer_format_into_system_message(self):
        system = messages("# Правила", "контекст", "[]")[0]
        expected = ("system", True, True)
        actual = (system["role"], system["content"].startswith("# Правила"), '"findings"' in system["content"])
        self.assertEqual(expected, actual, "правила и формат ответа — в системном сообщении")

    def test_puts_context_and_previous_into_user_message(self):
        user = messages("правила", "# Дифф", '[{"id": "T1"}]')[1]
        expected = (True, True)
        actual = ("# Дифф" in user["content"], '"T1"' in user["content"])
        self.assertEqual(expected, actual, "дифф и прошлые замечания — в запросе")

    def test_marks_missing_previous_as_empty_list(self):
        user = messages("правила", "контекст", "  ")[1]
        expected = "[]"
        actual = user["content"][-2:]
        self.assertEqual(expected, actual, "без прошлых замечаний — пустой список")

    def test_requests_json_answer(self):
        body = payload("deepseek-flash", [])
        expected = {"type": "json_object"}
        actual = body["response_format"]
        self.assertEqual(expected, actual, "ответ строго JSON")

    def test_accepts_answer_by_schema(self):
        text = answer(findings=[finding(3)], previous=[{"id": "T1", "fixed": True}])
        expected = json.loads(text)
        actual = verdict(text)
        self.assertEqual(expected, actual, "ответ по схеме принимается как есть")

    def test_rejects_answer_without_summary(self):
        text = json.dumps({"findings": [], "previous": []})
        error = None
        try:
            verdict(text)
        except ValueError as raised:
            error = raised
        self.assertIsInstance(error, ValueError, "без итога ответ не принимается")

    def test_rejects_finding_on_line_zero(self):
        text = answer(findings=[finding(0)])
        error = None
        try:
            verdict(text)
        except ValueError as raised:
            error = raised
        self.assertIsInstance(error, ValueError, "строка 0 не встанет в дифф")

    def test_rejects_previous_without_status(self):
        text = answer(previous=[{"id": "T1"}])
        error = None
        try:
            verdict(text)
        except ValueError as raised:
            error = raised
        self.assertIsInstance(error, ValueError, "прошлое замечание без статуса не закрыть")

    def test_names_broken_field_in_error(self):
        text = answer(findings=[finding("56")])
        message = ""
        try:
            verdict(text)
        except ValueError as raised:
            message = str(raised)
        expected = "'line': '56'"
        self.assertIn(expected, message, "в логе видно, что именно не так")

    def test_rejects_null_finding(self):
        text = answer(findings=[None])
        error = None
        try:
            verdict(text)
        except ValueError as raised:
            error = raised
        self.assertIsInstance(error, ValueError, "null вместо замечания — ошибка схемы, а не падение")

    def test_rejects_previous_given_as_string(self):
        text = answer(previous=["T1"])
        error = None
        try:
            verdict(text)
        except ValueError as raised:
            error = raised
        self.assertIsInstance(error, ValueError, "строка вместо прошлого замечания — ошибка схемы")

    def test_writes_checked_verdict_to_file(self):
        text = answer(findings=[finding(3)])
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            write_verdict(text, str(out))
            actual = json.loads(out.read_text(encoding="utf-8"))
        expected = json.loads(text)
        self.assertEqual(expected, actual, "в файл попадает ровно проверенный вердикт")

    def test_prints_verdict_without_file(self):
        text = answer()
        printed = io.StringIO()
        with contextlib.redirect_stdout(printed):
            write_verdict(text)
        expected = json.loads(text)
        actual = json.loads(printed.getvalue())
        self.assertEqual(expected, actual, "без файла вердикт печатается, как раньше")

    def test_takes_output_file_from_first_argument(self):
        expected = ("verdict.json", "")
        actual = (output_path(["review.py", "verdict.json"]), output_path(["review.py"]))
        self.assertEqual(expected, actual, "файл — первый аргумент, без него — печать")

    def test_delivers_reply_to_output_file(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            run(str(out), inputs(folder), replying(answer(), []), env())
            actual = json.loads(out.read_text(encoding="utf-8"))
        expected = json.loads(answer())
        self.assertEqual(expected, actual, "ответ модели доходит до файла вердикта")

    def test_removes_stale_verdict_when_request_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            out.write_text(answer(), encoding="utf-8")
            with contextlib.suppress(TimeoutError):
                run(str(out), inputs(folder), failing, env())
            exists = out.exists()
        self.assertFalse(exists, "упавший запрос не оставляет вердикт прошлого запуска")

    def test_writes_no_file_for_broken_answer(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            with contextlib.suppress(ValueError):
                write_verdict(answer(findings=[finding(0)]), str(out))
            exists = out.exists()
        self.assertFalse(exists, "ответ не по схеме не попадает в файл публикации")

    def test_sends_inputs_key_and_model_to_their_places(self):
        sent = []
        with tempfile.TemporaryDirectory() as folder:
            run(str(Path(folder, "verdict.json")), inputs(folder), replying(answer(), sent), env())
        expected = [("k", "deepseek-flash", (True, True, True))]
        self.assertEqual(expected, sent, "ключ и модель из окружения, правила, контекст и прошлые — на своих местах")

    def test_retries_after_broken_json(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            run(str(out), inputs(folder), replies(['{"summary": "обрыв', answer(summary="второй")], []), env())
            actual = json.loads(out.read_text(encoding="utf-8"))["summary"]
        expected = "второй"
        self.assertEqual(expected, actual, "битый JSON модели — повтор, а не падение ревью")

    def test_shows_model_its_own_error_on_retry(self):
        chats = []
        with tempfile.TemporaryDirectory() as folder:
            run(str(Path(folder, "v.json")), inputs(folder), replies([answer(previous=["T1"]), answer()], chats), env())
        first, retry = chats[1][:2], chats[1][2:]
        expected = (chats[0], "assistant", answer(previous=["T1"]), "user", True)
        actual = (first, retry[0]["role"], retry[0]["content"], retry[1]["role"], "прошлое T1" in retry[1]["content"])
        self.assertEqual(
            expected, actual, "повтор продолжает тот же разговор: правила и контекст, ответ модели и что в нём не так"
        )

    def test_gives_up_after_three_attempts(self):
        chats = []
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder, "verdict.json")
            message = ""
            try:
                run(str(out), inputs(folder), replies(["{"] * 4, chats), env())
            except ValueError as error:
                message = str(error)
            outcome = (len(chats), out.exists(), message)
        expected = (3, False, "нет ответа по схеме за 3 попытки")
        self.assertEqual(expected, outcome, "три неудачи подряд — ревью падает, файла нет")

    def test_warns_about_rejected_attempt_in_log(self):
        printed = io.StringIO()
        with tempfile.TemporaryDirectory() as folder, contextlib.redirect_stdout(printed):
            run(str(Path(folder, "v.json")), inputs(folder), replies([answer(previous=["T1"]), answer()], []), env())
        expected = "::warning title=ИИ-ревью::попытка 1: ответ не по схеме: прошлое T1"
        self.assertIn(expected, printed.getvalue(), "в логе видно, какая попытка отклонена и почему")

    def test_logs_tokens_of_each_attempt(self):
        printed = io.StringIO()
        with tempfile.TemporaryDirectory() as folder, contextlib.redirect_stderr(printed):
            run(str(Path(folder, "v.json")), inputs(folder), replies(["{", answer()], []), env())
        expected = 'попытка 2, токены: {"total_tokens": 2}'
        self.assertIn(expected, printed.getvalue(), "расход токенов виден по каждой попытке")


if __name__ == "__main__":
    unittest.main()
