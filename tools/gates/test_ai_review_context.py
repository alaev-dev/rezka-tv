import unittest

from gates.ai_review_context import call_lines, changed_names, context, file_section, tested_source, text_files


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


if __name__ == "__main__":
    unittest.main()
