import unittest

from gates import discipline as gate

PATH = "app/src/test/java/dev/a/FooTest.kt"


def kotlin(body: str, name: str = "formatsShortTime") -> str:
    return f"package dev.a\n\nclass FooTest {{\n    @Test\n    fun {name}() {{\n{body}    }}\n}}\n"


GOOD = '        val ms = 5L\n        assertThat("пять", Foo.of(ms), equalTo(five))\n'


class DisciplineTest(unittest.TestCase):
    def test_accepts_test_that_follows_rules(self):
        self.assertEqual({}, gate.problems(PATH, kotlin(GOOD)), "тест по правилам проходит")

    def test_reports_name_that_is_not_a_sentence(self):
        found = gate.problems(PATH, kotlin(GOOD, name="format"))
        self.assertIn("имя format", found[f"{PATH}::format"][0], "имя — предложение с глагола")

    def test_reports_name_that_does_not_start_with_verb(self):
        found = gate.problems(PATH, kotlin(GOOD, name="dpadOnlyInPlayerMode"))
        self.assertIn("имя dpadOnlyInPlayerMode", found[f"{PATH}::dpadOnlyInPlayerMode"][0], "первое слово — глагол")

    def test_reports_several_asserts(self):
        body = GOOD + '        assertThat("ещё", a, equalTo(b))\n'
        found = gate.problems(PATH, kotlin(body))
        self.assertIn("assert в тесте: 2, нужен ровно один", found[f"{PATH}::formatsShortTime"], "один assert")

    def test_reports_assert_that_is_not_last(self):
        body = GOOD + "        cleanUp()\n"
        found = gate.problems(PATH, kotlin(body))
        self.assertIn("assert должен быть последним", found[f"{PATH}::formatsShortTime"], "assert последним")

    def test_reports_assert_without_message(self):
        found = gate.problems(PATH, kotlin("        assertEquals(five, Foo.of(ms))\n"))
        expected = 'нужен assertThat("сообщение", фактическое, матчер)'
        self.assertIn(expected, found[f"{PATH}::formatsShortTime"], "assert с сообщением через assertThat")

    def test_reports_literal_inside_assert(self):
        found = gate.problems(PATH, kotlin('        assertThat("пять", Foo.of(ms), equalTo("0:05"))\n'))
        expected = "литерал в assert: вынесите значение в переменную"
        self.assertIn(expected, found[f"{PATH}::formatsShortTime"], "литерал в assert запрещён")

    def test_ignores_literal_in_message(self):
        found = gate.problems(PATH, kotlin('        assertThat("ровно 5 секунд", Foo.of(ms), equalTo(five))\n'))
        self.assertEqual({}, found, "сообщение — единственная строка в assert")

    def test_reports_long_test(self):
        body = "        val a = 1\n" * 12 + '        assertThat("a", a, equalTo(b))\n'
        found = gate.problems(PATH, kotlin(body))
        self.assertIn("тест длиннее 12 строк: 13", found[f"{PATH}::formatsShortTime"], "тест не длиннее 12 строк")

    def test_reports_setup_method(self):
        text = kotlin(GOOD).replace("    @Test", "    @BeforeEach\n    fun prepare() = Unit\n\n    @Test")
        expected = ["@Before/@After запрещены: готовьте данные в самом тесте"]
        self.assertEqual(expected, gate.problems(PATH, text)[f"{PATH}::(файл)"], "setUp запрещён")

    def test_reports_constant_in_test_class(self):
        text = kotlin(GOOD).replace("class FooTest {\n", "class FooTest {\n    private val five = 5\n")
        expected = ["константы и поля в тестах запрещены: значения — в самом тесте"]
        self.assertEqual(expected, gate.problems(PATH, text)[f"{PATH}::(файл)"], "поле в классе теста запрещено")

    def test_reads_expression_body_test(self):
        text = "package dev.a\n\nclass FooTest {\n    @Test\n    fun parses() = assertEquals(1, f())\n}\n"
        self.assertEqual("parses", gate.test_bodies(text)[0][0], "тест-выражение тоже проверяется")

    def test_reads_test_with_arguments_and_other_annotations(self):
        text = "class FooTest {\n    @Test(expected = E::class) @Timeout(1)\n    fun failsOnBadInput() {\n    }\n}\n"
        self.assertEqual("failsOnBadInput", gate.test_bodies(text)[0][0], "аннотации после @Test не прячут тест")

    def test_skips_escaped_quote_inside_string(self):
        body = '        val s = "a\\"}"\n        assertThat("строка", f(s), equalTo(b))\n'
        self.assertEqual({}, gate.problems(PATH, kotlin(body)), "экранированная кавычка не сбивает скобки")

    def test_accepts_old_test_from_ledger(self):
        found = {f"{PATH}::format": ["имя"]}
        self.assertEqual([], gate.violations(found, {f"{PATH}::format"}), "старый тест из журнала допустим")

    def test_reports_new_test_outside_ledger(self):
        found = {f"{PATH}::format": ["имя"]}
        self.assertEqual([(f"{PATH}::format", "имя")], gate.violations(found, set()), "новый тест по правилам")

    def test_reports_fixed_test_left_in_ledger(self):
        expected = [(f"{PATH}::format", f"тест в порядке: уберите {PATH}::format из {gate.LEDGER}")]
        self.assertEqual(expected, gate.violations({}, {f"{PATH}::format"}), "исправленный тест убирается")


def python(body: str, name: str = "test_rejects_expired_code") -> str:
    return f"import unittest\n\n\nclass FooTest(unittest.TestCase):\n    def {name}(self):\n{body}"


def messages(body: str, name: str = "test_rejects_expired_code") -> list:
    return gate.python_problems("t.py", python(body, name)).get(f"t.py::FooTest.{name}", [])


class PythonDisciplineTest(unittest.TestCase):
    def test_accepts_python_test_that_follows_rules(self):
        expected = []
        body = "        expected = []\n        self.assertEqual(expected, f(), 'пусто')\n"
        self.assertEqual(expected, messages(body), "тест по правилам проходит")

    def test_reports_python_name_without_verb(self):
        expected = "имя test_format: предложение о поведении с глагола, например rejectsExpiredCode"
        name = "test_format"
        self.assertIn(expected, messages("        self.assertTrue(f(), 'да')\n", name), "имя — с глагола")

    def test_reports_python_literal_in_assert(self):
        expected = "литерал в assert: вынесите значение в переменную"
        body = "        self.assertEqual([2], f(), 'вторая')\n"
        self.assertIn(expected, messages(body), "литерал в assert запрещён")

    def test_reports_python_assert_without_message(self):
        expected = "нужно сообщение последним аргументом assert"
        body = "        self.assertTrue(f())\n"
        self.assertIn(expected, messages(body), "assert с сообщением")

    def test_reports_python_assert_that_is_not_last(self):
        expected = "assert должен быть последним"
        body = "        self.assertTrue(f(), 'да')\n        g()\n"
        self.assertIn(expected, messages(body), "assert последним")

    def test_reports_python_setup_method(self):
        expected = ["setUp/tearDown запрещены: готовьте данные в самом тесте"]
        text = python("        self.assertTrue(f(), 'да')\n") + "\n    def setUp(self):\n        pass\n"
        self.assertEqual(expected, gate.python_problems("t.py", text)["t.py::(файл)"], "setUp запрещён")

    def test_reports_python_module_constant(self):
        expected = ["константы и поля в тестах запрещены: значения — в самом тесте"]
        text = "X = 1\n\n\n" + python("        self.assertTrue(f(), 'да')\n")
        self.assertEqual(expected, gate.python_problems("t.py", text)["t.py::(файл)"], "константа модуля запрещена")

    def test_reports_long_python_test(self):
        expected = "тест длиннее 12 строк: 13"
        body = "        a = 1\n" * 12 + "        self.assertTrue(a, 'да')\n"
        self.assertIn(expected, messages(body), "тест не длиннее 12 строк")

    def test_reports_python_constant_mistaken_for_message(self):
        expected = "нужно сообщение последним аргументом assert"
        body = "        self.assertEqual(f(), 'x')\n"
        self.assertIn(expected, messages(body), "второй аргумент assertEqual — не сообщение")

    def test_accepts_python_message_keyword(self):
        expected = []
        body = "        self.assertEqual(want, f(), msg='совпадает')\n"
        self.assertEqual(expected, messages(body), "сообщение через msg= принимается")

    def test_reports_two_python_asserts(self):
        expected = "assert в тесте: 2, нужен ровно один"
        body = "        self.assertTrue(f(), 'да')\n        self.assertTrue(g(), 'да')\n"
        self.assertIn(expected, messages(body), "один assert на тест")
