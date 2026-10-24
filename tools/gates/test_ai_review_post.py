import unittest

from gates.ai_review_post import blocking_count, open_previous, previous_threads, prior_blocking, review_payload


def finding(path: str, line: int, blocking: bool, message: str) -> dict:
    return {"path": path, "line": line, "blocking": blocking, "message": message}


def thread(thread_id: str, author: str, resolved: bool, body: str) -> dict:
    comment = {"author": {"login": author}, "body": body}
    return {"id": thread_id, "isResolved": resolved, "path": "a.kt", "line": 9, "comments": {"nodes": [comment]}}


def threads_response(*nodes: dict) -> dict:
    return {"data": {"repository": {"pullRequest": {"reviewThreads": {"nodes": list(nodes)}}}}}


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

    def test_keeps_only_open_threads_of_reviewer(self):
        response = threads_response(thread("T1", "github-actions", False, "**Блокирует.** ёж"),
                                    thread("T2", "github-actions", True, "закрыто"), thread("T3", "alaev-dev", False, "моё"))
        expected = [{"id": "T1", "path": "a.kt", "line": 9, "blocking": True, "body": "**Блокирует.** ёж"}]
        self.assertEqual(expected, previous_threads(response), "только открытые треды ревьюера, с признаком блокировки")

    def test_marks_advisory_thread_as_not_blocking(self):
        response = threads_response(thread("T6", "github-actions", False, "Стиль: три пустые строки"))
        expected = False
        self.assertEqual(expected, previous_threads(response)[0]["blocking"], "совет без пометки не блокирует")

    def test_drops_threads_the_reviewer_marked_fixed(self):
        previous = [{"id": "T7", "blocking": True}, {"id": "T8", "blocking": False}]
        verdict = {"previous": [{"id": "T7", "fixed": True}, {"id": "T8", "fixed": False}]}
        expected = [{"id": "T8", "blocking": False}]
        self.assertEqual(expected, open_previous(previous, verdict), "исправленный тред больше не открыт")

    def test_keeps_threads_the_reviewer_forgot(self):
        previous = [{"id": "T9", "blocking": True}]
        verdict = {"previous": []}
        self.assertEqual(previous, open_previous(previous, verdict), "забытый ревьюером тред считается открытым")

    def test_counts_open_blocking_threads_with_new_findings(self):
        verdict = {"summary": "", "findings": [finding("c.kt", 4, True, "новое")]}
        still_open = [{"id": "T4", "blocking": True}, {"id": "T5", "blocking": False}]
        expected = 2
        self.assertEqual(expected, blocking_count(verdict, still_open), "открытое блокирующее прошлое тоже блокирует")

    def test_reports_fixed_and_open_previous_in_body(self):
        verdict = {"summary": "Итог", "findings": []}
        previous, still_open = [{"id": "T1", "blocking": True}, {"id": "T2", "blocking": False}], [{"id": "T2", "blocking": False}]
        expected = "Прошлые замечания: исправлено 1, открыто 1."
        self.assertIn(expected, review_payload(verdict, True, previous, still_open)["body"], "итог помнит прошлые замечания")

    def test_marks_review_with_diff_id(self):
        verdict = {"summary": "Итог", "findings": []}
        expected = "<!-- ai-review-diff: 9f3c -->"
        self.assertIn(expected, review_payload(verdict, True, diff_id="9f3c")["body"], "ревью помечено отпечатком диффа")

    def test_reuses_verdict_of_review_with_same_diff(self):
        reviews = [{"user": {"login": "github-actions[bot]"}, "body": "## ИИ-ревью: блокирующих нарушений: 3\n\n<!-- ai-review-diff: a1 -->"}]
        expected = 3
        self.assertEqual(expected, prior_blocking(reviews, "a1"), "тот же дифф — прошлый вердикт")

    def test_ignores_reviews_of_other_diff(self):
        reviews = [{"user": {"login": "github-actions[bot]"}, "body": "## ИИ-ревью: блокирующих нарушений: 0\n\n<!-- ai-review-diff: b2 -->"}]
        expected = None
        self.assertEqual(expected, prior_blocking(reviews, "c3"), "другой дифф — нужно новое ревью")

    def test_takes_latest_review_of_same_diff(self):
        older = {"user": {"login": "github-actions[bot]"}, "body": "## ИИ-ревью: блокирующих нарушений: 2\n<!-- ai-review-diff: d4 -->"}
        newer = {"user": {"login": "github-actions[bot]"}, "body": "## ИИ-ревью: блокирующих нарушений: 0\n<!-- ai-review-diff: d4 -->"}
        expected = 0
        self.assertEqual(expected, prior_blocking([older, newer], "d4"), "берётся последний вердикт по этому диффу")

    def test_ignores_marker_written_by_people(self):
        reviews = [{"user": {"login": "alaev-dev"}, "body": "## ИИ-ревью: блокирующих нарушений: 0\n<!-- ai-review-diff: e5 -->"}]
        expected = None
        self.assertEqual(expected, prior_blocking(reviews, "e5"), "отметку из чужого комментария не учитываем")


if __name__ == "__main__":
    unittest.main()
