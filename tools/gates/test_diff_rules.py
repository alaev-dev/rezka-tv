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

    def test_reports_code_change_with_only_reformatted_test(self):
        code, test = "app/src/main/java/A.kt", "app/src/test/java/ATest.kt"
        before, after = {code: "val a = 1\n", test: "val b = 1\n"}, {code: "val a = 2\n", test: "val  b = 1\n"}
        expected = [f"код меняется без тестов: {code}"]
        self.assertEqual(expected, gate.untested_change(before, after), "пробелы в тесте не считаются тестом")

    def test_reports_tests_lost_in_moved_file(self):
        old, new = "app/src/test/a/ATest.kt", "app/src/test/b/ATest.kt"
        before, after = {old: "@Test fun a()\n@Test fun b()\n"}, {new: "@Test fun a()\n"}
        expected = ["тестов стало меньше: было 2, стало 1; тесты не удаляют, а исправляют"]
        self.assertEqual(expected, gate.lost_tests(before, after), "перенос файла не прячет потерю тестов")

    def test_accepts_removed_kdoc_without_tests(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: "/**\n * Описание.\n */\nval a = 1 // пояснение\n"}, {path: "val a = 1\n"}
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "удаление комментариев не меняет поведение")

    def test_accepts_removed_python_comment_and_docstring(self):
        path = "tools/gates/a.py"
        before, after = (
            {path: 'def a():\n    """Описание."""\n    return 1  # да\n'},
            {path: "def a():\n    return 1\n"},
        )
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "докстринг и комментарий — не поведение")

    def test_reports_code_changed_next_to_removed_comment(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: "val a = 1 // пояснение\n"}, {path: "val a = 2\n"}
        expected = [f"код меняется без тестов: {path}"]
        self.assertEqual(expected, gate.untested_change(before, after), "рядом с комментарием поменялся код")

    def test_keeps_comment_markers_inside_strings(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: 'val u = "http://a"\n'}, {path: 'val u = "http:"\n'}
        expected = [f"код меняется без тестов: {path}"]
        self.assertEqual(expected, gate.untested_change(before, after), "// внутри строки — не комментарий")

    def test_accepts_removed_comment_at_end_of_file(self):
        path = "app/src/main/java/A.kt"
        before, after = {path: "val a = 1\n// конец"}, {path: "val a = 1\n"}
        expected = []
        self.assertEqual(expected, gate.untested_change(before, after), "комментарий без перевода строки в конце")

    def test_reads_only_code_and_tests(self):
        paths = ["gradle/wrapper/gradle-wrapper.jar", "docs/a.jpg", "app/src/main/java/A.kt", "tools/test_a.py"]
        expected = ["app/src/main/java/A.kt", "tools/test_a.py"]
        self.assertEqual(expected, gate.relevant(paths), "двоичные файлы и ресурсы гейт не читает")
