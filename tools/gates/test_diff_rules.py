import unittest

from gates import diff_rules as gate


class DiffRulesTest(unittest.TestCase):
    def test_counts_kotlin_and_python_tests(self):
        sources = {"app/src/test/A.kt": "@Test\nfun a() {}\n@Test\nfun b() {}\n", "tools/test_b.py": "def test_c():\n"}
        expected = 3
        actual = sum(gate.test_count(path, text) for path, text in sources.items())
        self.assertEqual(expected, actual, "считаются @Test в Kotlin и def test_ в Python")

    def test_ignores_tests_outside_test_sources(self):
        path, text = "app/src/main/A.kt", "@Test\n"
        expected = 0
        self.assertEqual(expected, gate.test_count(path, text), "в коде приложения тестов не бывает")

    def test_accepts_split_of_one_test_into_three(self):
        path = "app/src/test/A.kt"
        before, after = {path: "@Test fun a()\n"}, {path: "@Test fun b()\n@Test fun c()\n@Test fun d()\n"}
        expected = []
        self.assertEqual(expected, gate.lost_tests(before, after), "перевод теста не уменьшает их число")

    def test_accepts_renamed_test(self):
        path = "app/src/test/A.kt"
        before, after = {path: "@Test fun a()\n"}, {path: "@Test fun formatsTime()\n"}
        expected = []
        self.assertEqual(expected, gate.lost_tests(before, after), "переименование теста не потеря")

    def test_reports_removed_test(self):
        path = "app/src/test/A.kt"
        before, after = {path: "@Test fun a()\n@Test fun b()\n"}, {path: "@Test fun a()\n"}
        expected = ["тестов стало меньше: было 2, стало 1; тесты не удаляют, а исправляют"]
        self.assertEqual(expected, gate.lost_tests(before, after), "удалять тесты нельзя")

    def test_reports_code_change_without_tests(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: "val a = 1\n"}, {path: "val a = 2\n"}
        expected = [f"код меняется без тестов: {path}"]
        self.assertEqual(expected, gate.untested_change(before, after), "изменение кода требует тестов")

    def test_accepts_code_change_with_tests(self):
        code, test = "app/src/main/java/A.kt", "app/src/test/java/ATest.kt"
        before, after = {code: "val a = 1\n", test: "x\n"}, {code: "val a = 2\n", test: "y\n"}
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "код вместе с тестами проходит")

    def test_accepts_reformatting_without_tests(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: "fun a(x: Int, y: Int) = 1\n"}, {path: "fun a(\n    x: Int,\n    y: Int\n) = 1\n"}
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "перенос строк — не изменение поведения")

    def test_reports_new_code_file_without_tests(self):
        path = "tools/gates/new_gate.py"
        before, after = {}, {path: "x = 1\n"}
        expected = [f"код меняется без тестов: {path}"]
        self.assertEqual(expected, gate.untested_change(before, after), "новый файл кода — тоже изменение")

    def test_ignores_resources_and_docs(self):
        before, after = {"README.md": "a\n", "app/src/main/res/a.xml": "<a/>\n"}, {"README.md": "b\n"}
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "ресурсы и документация тестов не требуют")
