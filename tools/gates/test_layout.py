import unittest

from gates import layout as gate

MAIN = "app/src/main/java/dev/a"
TEST = "app/src/test/java/dev/a"


class LayoutTest(unittest.TestCase):
    def test_accepts_class_named_like_its_file(self):
        sources = {f"{MAIN}/Foo.kt": "package dev.a\n\nclass Foo\n"}
        self.assertEqual({}, gate.problems(sources), "класс в файле со своим именем — порядок")

    def test_accepts_file_with_only_functions(self):
        sources = {f"{MAIN}/Navigation.kt": "package dev.a\n\nfun open() = Unit\n"}
        self.assertEqual({}, gate.problems(sources), "файл из одних функций допустим")

    def test_ignores_nested_types(self):
        sources = {f"{MAIN}/Foo.kt": "package dev.a\n\nclass Foo {\n    data class Bar(val x: Int)\n}\n"}
        self.assertEqual({}, gate.problems(sources), "вложенный класс не верхнеуровневый")

    def test_reports_package_that_differs_from_directory(self):
        path = f"{MAIN}/b/Foo.kt"
        found = gate.problems({path: "package dev.a\n\nclass Foo\n"})
        self.assertIn("пакет dev.a не совпадает с каталогом dev.a.b", found[path], "пакет сверяется с каталогом")

    def test_reports_several_top_level_types(self):
        path = f"{MAIN}/Model.kt"
        found = gate.problems({path: "package dev.a\n\ndata class Card(val id: String)\nenum class Sort { NEW }\n"})
        self.assertIn("несколько верхнеуровневых классов (Card, Sort): по одному на файл", found[path], "два класса")

    def test_reports_class_in_file_with_other_name(self):
        path = f"{MAIN}/Watched.kt"
        found = gate.problems({path: "package dev.a\n\ninternal class WatchedStore\n"})
        expected = "класс WatchedStore лежит в Watched.kt: имя файла должно совпадать с именем класса"
        self.assertIn(expected, found[path], "имя файла сверяется с классом")

    def test_counts_fun_interface_and_object(self):
        source = "package dev.a\n\nfun interface Click\nprivate object Keys\n"
        self.assertEqual(["Click", "Keys"], gate.top_level_types(source), "fun interface и object — классы")

    def test_reports_test_without_mirrored_source(self):
        path = f"{TEST}/CookiesTest.kt"
        found = gate.problems({path: "package dev.a\n\nclass CookiesTest\n"})
        self.assertIn(f"тест без исходника: нет {MAIN}/Cookies.kt", found[path], "тест лежит зеркально исходнику")

    def test_accepts_test_with_mirrored_source(self):
        sources = {
            f"{MAIN}/Foo.kt": "package dev.a\n\nclass Foo\n",
            f"{TEST}/FooTest.kt": "package dev.a\n\nclass FooTest\n",
        }
        self.assertEqual({}, gate.problems(sources), "у теста есть исходник в том же пакете")

    def test_accepts_test_helper_without_suffix(self):
        sources = {f"{TEST}/FakeClock.kt": "package dev.a\n\nclass FakeClock\n"}
        self.assertEqual({}, gate.problems(sources), "помощник тестов без суффикса Test не сверяется")

    def test_skips_files_outside_source_roots(self):
        sources = {"build.gradle.kts": "plugins {}\n", "tools/Foo.kt": "class Bar\n"}
        self.assertEqual({}, gate.problems(sources), "вне src/*/java раскладка не проверяется")

    def test_accepts_problem_file_from_ledger(self):
        found = {f"{MAIN}/Model.kt": ["несколько классов"]}
        self.assertEqual([], gate.violations(found, {f"{MAIN}/Model.kt"}), "старый долг из журнала допустим")

    def test_reports_problem_file_outside_ledger(self):
        found = {f"{MAIN}/Model.kt": ["несколько классов"]}
        expected = [(f"{MAIN}/Model.kt", "несколько классов")]
        self.assertEqual(expected, gate.violations(found, set()), "новое нарушение роняет сборку")

    def test_reports_fixed_file_left_in_ledger(self):
        expected = [(f"{MAIN}/Foo.kt", f"раскладка в порядке: уберите файл из {gate.LEDGER}")]
        self.assertEqual(expected, gate.violations({}, {f"{MAIN}/Foo.kt"}), "исправленный файл убирается из журнала")


if __name__ == "__main__":
    unittest.main()
