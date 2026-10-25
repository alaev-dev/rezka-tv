import subprocess
import tempfile
import unittest
from pathlib import Path

from gates.ai_review_context import (
    call_lines,
    changed_names,
    context,
    file_section,
    history_section,
    main,
    test_for,
    tested_source,
    text_files,
)
from gates.test_diff_rules import commit_all


def write(repo: str, path: str, text: str) -> None:
    Path(repo, path).parent.mkdir(parents=True, exist_ok=True)
    Path(repo, path).write_text(text, encoding="utf-8")


def reviewed_repo(repo: str) -> str:
    code = "app/src/main/java/a/Foo.kt"
    subprocess.run(["git", "init", "-q", "-b", "main", repo], check=True)
    write(repo, "app/src/test/java/a/FooTest.kt", "class FooTest\n")
    write(repo, "app/src/main/java/a/Caller.kt", "val x = foo()\n")
    for n in range(1, 8):
        write(repo, code, f"fun foo() = {n}\n")
        commit_all(repo, f"Код: версия {n}")
    write(repo, code, "fun foo() = 99\n")
    commit_all(repo, "PR: foo возвращает 99")
    write(repo, "description.md", "# PR\n")
    main("main~1", "description.md", repo)
    return Path(repo, ".ai-review-pr.md").read_text(encoding="utf-8")


class AiReviewContextTest(unittest.TestCase):
    def test_finds_changed_definitions_in_diff(self):
        diff = "+fun decodePoint(raw: ByteArray)\n-    private fun unsigned(n: BigInteger)\n+def main(base):\n val keep = 1\n"
        expected = ["decodePoint", "main", "unsigned"]
        actual = changed_names(diff)
        self.assertEqual(expected, actual, "из диффа берутся изменённые функции")

    def test_ignores_file_headers_in_diff(self):
        diff = "+++ b/fun Odd.kt\n--- a/class Odd.kt\n"
        expected = []
        actual = changed_names(diff)
        self.assertEqual(expected, actual, "заголовки файлов диффа — не определения")

    def test_drops_calls_inside_changed_files(self):
        grep = "A.kt:3:decodePoint(x)\nB.kt:9:decodePoint(y)\n"
        expected = ["B.kt:9:decodePoint(y)"]
        actual = call_lines(grep, ["A.kt"])
        self.assertEqual(expected, actual, "изменённые файлы и так приложены целиком")

    def test_caps_number_of_calls(self):
        grep = "".join(f"B.kt:{n}:f()\n" for n in range(1, 50))
        expected = 20
        actual = len(call_lines(grep, []))
        self.assertEqual(expected, actual, "частый вызов не раздувает запрос")

    def test_attaches_changed_file_whole(self):
        section = file_section("A.kt", "val a = 1\nval b = 2\n")
        expected = "    2  val b = 2"
        self.assertIn(expected, section, "изменённый файл приложен целиком, с номерами строк")

    def test_skips_huge_file(self):
        text = "x\n" * 601
        section = file_section("Big.kt", text)
        marker = "целиком не приложен"
        self.assertIn(marker, section, "огромный файл не раздувает контекст")

    def test_puts_description_diff_and_files_together(self):
        page = context("Заголовок", "+val a = 1", {"A.kt": "val a = 1\n"}, {"f": ["B.kt:9:f()"]})
        expected = (True, True, True, True)
        actual = ("Заголовок" in page, "+val a = 1" in page, "### A.kt" in page, "B.kt:9:f()" in page)
        self.assertEqual(expected, actual, "всё для ревью — в одном файле")

    def test_skips_binary_files(self):
        numstat = "-\t-\tgradle/wrapper/gradle-wrapper.jar\n1\t0\tREADME.md\n"
        expected = ["README.md"]
        actual = text_files(numstat)
        self.assertEqual(expected, actual, "двоичные файлы не прикладываются")

    def test_attaches_kotlin_source_under_test(self):
        path = "app/src/test/java/dev/alaev/rezkatv/rezka/login/LoginCryptoTest.kt"
        expected = "app/src/main/java/dev/alaev/rezkatv/rezka/login/LoginCrypto.kt"
        actual = tested_source(path)
        self.assertEqual(expected, actual, "к тесту прикладывается проверяемый код")

    def test_attaches_python_source_under_test(self):
        path = "tools/gates/test_diff_rules.py"
        expected = "tools/gates/diff_rules.py"
        actual = tested_source(path)
        self.assertEqual(expected, actual, "к тесту гейта прикладывается гейт")

    def test_attaches_nothing_for_regular_source(self):
        path = "app/src/main/java/dev/alaev/rezkatv/rezka/Settings.kt"
        expected = ""
        actual = tested_source(path)
        self.assertEqual(expected, actual, "у обычного кода нет пары")

    def test_takes_name_of_extension_function(self):
        diff = "+fun Activity.resume(session: WatchSession) {\n+fun List<Card>?.firstFree(): Card?\n"
        expected = ["firstFree", "resume"]
        actual = changed_names(diff)
        self.assertEqual(expected, actual, "у расширения имя — после точки, а не тип-получатель")

    def test_takes_name_of_generic_function(self):
        diff = "+    suspend fun <T> load(block: () -> T): T\n+private inline fun <T> timed(name: String)\n"
        expected = ["load", "timed"]
        actual = changed_names(diff)
        self.assertEqual(expected, actual, "параметр типа перед именем не мешает")

    def test_takes_name_of_fun_interface(self):
        diff = "+fun interface Listener {\n"
        expected = ["Listener"]
        actual = changed_names(diff)
        self.assertEqual(expected, actual, "fun interface — имя интерфейса, а не слово interface")

    def test_attaches_kotlin_test_of_changed_code(self):
        expected = "app/src/test/java/dev/alaev/rezkatv/rezka/SettingsTest.kt"
        actual = test_for("app/src/main/java/dev/alaev/rezkatv/rezka/Settings.kt")
        self.assertEqual(expected, actual, "к изменённому коду прикладывается его тест")

    def test_attaches_python_test_of_changed_gate(self):
        expected = "tools/gates/test_comments.py"
        actual = test_for("tools/gates/comments.py")
        self.assertEqual(expected, actual, "к гейту прикладывается его тест")

    def test_finds_no_test_for_test(self):
        expected = ""
        actual = test_for("tools/gates/test_comments.py")
        self.assertEqual(expected, actual, "у теста своего теста нет")

    def test_reports_history_of_files(self):
        logs = {"A.kt": "1c9e980 2026-10-25 Гейты: двоичные файлы\n", "New.kt": ""}
        expected = (True, False)
        actual = ("1c9e980" in history_section(logs), "New.kt" in history_section(logs))
        self.assertEqual(expected, actual, "история есть у старых файлов, новые пропускаются")

    def test_collects_test_history_and_calls_of_reviewed_repo(self):
        with tempfile.TemporaryDirectory() as repo:
            page = reviewed_repo(repo)
        expected = (True, True, False, False, True)
        actual = tuple(m in page for m in ("a/FooTest.kt (", "версия 3", "версия 2", "PR: foo", "a/Caller.kt:1:"))
        self.assertEqual(expected, actual, "тест кода, 5 коммитов базы без коммитов PR и вызовы из этого репозитория")


if __name__ == "__main__":
    unittest.main()
