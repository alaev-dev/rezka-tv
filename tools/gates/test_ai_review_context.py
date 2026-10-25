import unittest

from gates.ai_review_context import context, deny_rules, file_section, text_files


class AiReviewContextTest(unittest.TestCase):
    def test_denies_reading_untouched_gates_and_build(self):
        rules = deny_rules(["app/src/main/java/A.kt"])
        expected = (True, True, True)
        actual = ("Read(./tools/**)" in rules, "Read(./README.md)" in rules, "Read(./build.gradle.kts)" in rules)
        self.assertEqual(expected, actual, "гейты, README и сборка закрыты, если PR их не трогает")

    def test_allows_reading_touched_directory(self):
        rules = deny_rules(["tools/gates/comments.py"])
        guarded = "Read(./tools/**)"
        self.assertNotIn(guarded, rules, "PR меняет гейты — их можно читать")

    def test_allows_reading_touched_file(self):
        rules = deny_rules(["detekt.yml"])
        guarded = "Read(./detekt.yml)"
        self.assertNotIn(guarded, rules, "PR меняет detekt.yml — его можно читать")

    def test_always_denies_web(self):
        rules = deny_rules(["tools/a.py", ".github/a.yml", "gradle/a", "README.md", "THIRD_PARTY.md", "detekt.yml"])
        expected = ["WebFetch", "WebSearch"]
        actual = rules[:2]
        self.assertEqual(expected, actual, "в сеть ревьюеру не нужно никогда")

    def test_attaches_changed_file_whole(self):
        text = "val a = 1\nval b = 2\n"
        section = file_section("A.kt", text)
        self.assertIn(text.rstrip(), section, "изменённый файл приложен целиком")

    def test_skips_huge_file(self):
        text = "x\n" * 601
        section = file_section("Big.kt", text)
        marker = "читай нужные места сам"
        self.assertIn(marker, section, "огромный файл не раздувает контекст")

    def test_puts_description_diff_and_files_together(self):
        page = context("# Заголовок", "+val a = 1", {"A.kt": "val a = 1\n"})
        expected = (True, True, True)
        actual = (page.startswith("# Заголовок"), "+val a = 1" in page, "### A.kt" in page)
        self.assertEqual(expected, actual, "всё для ревью — в одном файле")

    def test_skips_binary_files(self):
        numstat = "-\t-\tgradle/wrapper/gradle-wrapper.jar\n1\t0\tREADME.md\n"
        expected = ["README.md"]
        actual = text_files(numstat)
        self.assertEqual(expected, actual, "двоичные файлы не прикладываются")


if __name__ == "__main__":
    unittest.main()
