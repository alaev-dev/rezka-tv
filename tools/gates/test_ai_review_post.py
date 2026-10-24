import unittest

from gates.ai_review_post import blocking_count, review_payload


def finding(path: str, line: int, blocking: bool, message: str) -> dict:
    return {"path": path, "line": line, "blocking": blocking, "message": message}


class AiReviewPostTest(unittest.TestCase):

    def test_counts_only_blocking_findings(self):
        verdict = {"summary": "", "findings": [finding("a.kt", 7, True, "x"), finding("b.kt", 3, False, "y")]}
        expected = 1
        self.assertEqual(expected, blocking_count(verdict), "замечания без блокировки не считаются")

    def test_counts_nothing_without_findings(self):
        verdict = {"summary": "Чисто", "findings": []}
        expected = 0
        self.assertEqual(expected, blocking_count(verdict), "без замечаний блокировок нет")

    def test_attaches_inline_comment_to_new_side_of_line(self):
        verdict = {"summary": "", "findings": [finding("app/Ёж.kt", 41, False, "медленно")]}
        expected = {"path": "app/Ёж.kt", "line": 41, "side": "RIGHT", "body": "медленно"}
        self.assertEqual([expected], review_payload(verdict, True)["comments"], "замечание встаёт под строку")

    def test_marks_blocking_inline_comment(self):
        verdict = {"summary": "", "findings": [finding("tools/x.py", 9, True, "комментарий в коде")]}
        body = review_payload(verdict, True)["comments"][0]["body"]
        self.assertTrue(body.startswith("**Блокирует.**"), "блокирующее замечание видно сразу")

    def test_moves_findings_into_body_when_inline_is_impossible(self):
        verdict = {"summary": "Итог", "findings": [finding("res/values/strings.xml", 113, False, "не по-русски")]}
        expected = "`res/values/strings.xml:113` — не по-русски"
        self.assertIn(expected, review_payload(verdict, False)["body"], "замечание не теряется без строки диффа")

    def test_leaves_no_inline_comments_in_fallback(self):
        verdict = {"summary": "Итог", "findings": [finding("a.kt", 5, True, "ошибка")]}
        payload = review_payload(verdict, False)
        self.assertNotIn("comments", payload, "запасной вариант не повторяет отклонённые комментарии")

    def test_states_blocking_count_in_body(self):
        verdict = {"summary": "Две ошибки", "findings": [finding("a.kt", 1, True, "a"), finding("b.kt", 2, True, "b")]}
        expected = "блокирующих нарушений: 2"
        self.assertIn(expected, review_payload(verdict, True)["body"], "итог называет число блокировок")

    def test_posts_review_as_comment(self):
        verdict = {"summary": "Чисто", "findings": []}
        expected = "COMMENT"
        self.assertEqual(expected, review_payload(verdict, True)["event"], "статус блокирует сам, ревью — без вердикта")


if __name__ == "__main__":
    unittest.main()
