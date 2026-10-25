import json
import unittest

from gates.ai_review_request import messages, payload, verdict


def answer(**fields) -> str:
    base = {"summary": "итог", "findings": [], "previous": []}
    return json.dumps({**base, **fields}, ensure_ascii=False)


def finding(line) -> dict:
    return {"path": "A.kt", "line": line, "blocking": True, "message": "ошибка"}


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


if __name__ == "__main__":
    unittest.main()
