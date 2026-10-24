import tempfile
import unittest
from pathlib import Path

from gates import comments as gate


class CommentsTest(unittest.TestCase):

    def test_finds_line_comment_in_kotlin(self):
        source = 'val ёж = 7\n// пояснение\n'
        expected = [2]
        name = "A.kt"
        self.assertEqual(expected, gate.comment_lines(name, source), "строчный комментарий Kotlin найден")

    def test_finds_trailing_block_comment_in_kotlin(self):
        source = "fun f() = 3 /* три */\n"
        expected = [1]
        name = "B.kts"
        self.assertEqual(expected, gate.comment_lines(name, source), "блочный комментарий в конце строки найден")

    def test_ignores_slashes_inside_kotlin_string(self):
        source = 'val url = "https://rezka.test/films/"\n'
        expected = []
        name = "C.kt"
        self.assertEqual(expected, gate.comment_lines(name, source), "слеши в строке — не комментарий")

    def test_ignores_slashes_inside_raw_string(self):
        source = 'val js = """\n  a // b\n"""\n'
        expected = []
        name = "D.kt"
        self.assertEqual(expected, gate.comment_lines(name, source), "слеши в сырой строке — не комментарий")

    def test_counts_lines_after_multiline_string(self):
        source = 'val s = """\nx\ny\n"""\n// после\n'
        expected = [5]
        name = "E.kt"
        self.assertEqual(expected, gate.comment_lines(name, source), "номер строки учитывает многострочную строку")

    def test_finds_xml_comment(self):
        source = '<a>\n  <!-- старое -->\n</a>\n'
        expected = [2]
        name = "res/layout/f.xml"
        self.assertEqual(expected, gate.comment_lines(name, source), "XML-комментарий найден")

    def test_finds_hash_comment_after_yaml_value(self):
        source = "on:\n  push: main # ветка\n"
        expected = [2]
        name = ".github/workflows/g.yml"
        self.assertEqual(expected, gate.comment_lines(name, source), "комментарий YAML найден")

    def test_ignores_hash_inside_quotes(self):
        source = 'color: "#B60205"\n'
        expected = []
        name = "h.yml"
        self.assertEqual(expected, gate.comment_lines(name, source), "решётка в кавычках — не комментарий")

    def test_finds_python_comment_but_not_shebang(self):
        source = "#!/usr/bin/env python3\nx = 1  # единица\n"
        name = "i.py"
        expected = [2]
        self.assertEqual(expected, gate.comment_lines(name, source), "шебанг не комментарий, решётка после кода — да")

    def test_finds_python_function_docstring(self):
        source = 'def f():\n    """Пояснение."""\n    return 4\n'
        name = "j.py"
        expected = [2]
        self.assertEqual(expected, gate.comment_lines(name, source), "докстринг функции считается комментарием")

    def test_finds_python_class_docstring(self):
        source = 'class Ёж:\n    """Класс."""\n    x = 1\n'
        name = "m.py"
        expected = [2]
        self.assertEqual(expected, gate.comment_lines(name, source), "докстринг класса считается комментарием")

    def test_finds_python_async_function_docstring(self):
        source = 'async def f():\n    """Асинхронно."""\n    return 5\n'
        name = "n.py"
        expected = [2]
        self.assertEqual(expected, gate.comment_lines(name, source), "докстринг async-функции считается комментарием")

    def test_finds_python_module_docstring(self):
        source = '"""Модуль."""\nimport os\n'
        name = "k.py"
        expected = [1]
        self.assertEqual(expected, gate.comment_lines(name, source), "докстринг модуля считается комментарием")

    def test_ignores_hash_inside_python_string(self):
        source = 'tag = "#ёлка"\n'
        name = "l.py"
        expected = []
        self.assertEqual(expected, gate.comment_lines(name, source), "решётка в строке Python — не комментарий")

    def test_skips_unknown_file_types(self):
        source = "// не код\n"
        expected = []
        name = "README.md"
        self.assertEqual(expected, gate.comment_lines(name, source), "документация не проверяется")

    def test_ignores_slashes_inside_single_quoted_string(self):
        source = "const page = 'https://rezka.test/'\n"
        name = "F.js"
        expected = []
        self.assertEqual(expected, gate.comment_lines(name, source), "слеши в строке в одинарных кавычках — не комментарий")

    def test_ignores_slashes_inside_template_literal(self):
        source = "const u = `https://${host}/`\n"
        name = "G.mjs"
        expected = []
        self.assertEqual(expected, gate.comment_lines(name, source), "слеши в шаблонной строке JS — не комментарий")

    def test_treats_unclosed_raw_string_as_string(self):
        source = 'val s = """\n// внутри\n'
        name = "H.kt"
        expected = []
        self.assertEqual(expected, gate.comment_lines(name, source), "незакрытая сырая строка тянется до конца файла")

    def test_leaves_clean_files_out_of_scan(self):
        clean = Path(tempfile.mkdtemp(prefix="comments-")) / "clean.kt"
        clean.write_text("val чисто = 1\n", encoding="utf-8")
        expected = {}
        self.assertEqual(expected, gate.scan([str(clean)]), "файл без комментариев не попадает в результат")

    def test_skips_missing_paths_in_scan(self):
        missing = str(Path(tempfile.mkdtemp(prefix="comments-")) / "gone.kt")
        expected = {}
        self.assertEqual(expected, gate.scan([missing]), "удалённый из диска путь пропускается")

    def test_picks_scanner_for_every_code_type(self):
        paths = ["a.java", "b.js", "c.mjs", "d.properties", "e.pro", "f.toml", "g.yaml", ".gitignore", "res/h.xml"]
        expected = [gate.c_like_comments] * 3 + [gate.hash_comments] * 5 + [gate.xml_comments]
        self.assertEqual(expected, [gate.scanner_for(p) for p in paths], "у каждого типа кода свой сканер")

    def test_reads_undecodable_source_without_crashing(self):
        broken = Path(tempfile.mkdtemp(prefix="comments-")) / "broken.xml"
        broken.write_bytes(bytes([0xFF, 0xD8]) + "<!-- ё -->".encode())
        expected = {str(broken): [1]}
        self.assertEqual(expected, gate.scan([str(broken)]), "файл не в UTF-8 сканируется, а не роняет проверку")

    def test_reports_every_comment_outside_ledger(self):
        found = {"app/K.kt": [12, 40]}
        message = "комментарий запрещён: удалите его"
        expected = [("app/K.kt", 12, message), ("app/K.kt", 40, message)]
        nothing = set()
        self.assertEqual(expected, gate.violations(found, nothing, nothing), "каждый новый комментарий отмечен")

    def test_accepts_untouched_file_from_ledger(self):
        found = {"app/L.kt": [3]}
        ledger = {"app/L.kt"}
        nothing = set()
        expected = []
        self.assertEqual(expected, gate.violations(found, ledger, nothing), "старый долг в нетронутом файле допустим")

    def test_reports_changed_file_from_ledger(self):
        found = {"app/M.kt": [8]}
        ledger = {"app/M.kt"}
        expected = [("app/M.kt", 8, "файл правится: удалите в нём все комментарии и уберите его из журнала")]
        self.assertEqual(expected, gate.violations(found, ledger, ledger), "правка файла требует убрать в нём комментарии")

    def test_reports_clean_file_left_in_ledger(self):
        ledger = {"app/N.kt"}
        expected = [("app/N.kt", 1, "комментариев нет: уберите файл из quality/comments-baseline.txt")]
        clean, untouched = {}, set()
        self.assertEqual(expected, gate.violations(clean, ledger, untouched), "журнал долга только сокращается")

    def test_ignores_blank_lines_in_ledger(self):
        text = "app/O.kt\n\n  \napp/P.kt  \n"
        expected = {"app/O.kt", "app/P.kt"}
        self.assertEqual(expected, gate.parse_ledger(text), "пустые строки журнала — не пути")

    def test_formats_github_annotation(self):
        path, line, message = "app/Q.kt", 77, "удалите"
        expected = "::error file=app/Q.kt,line=77,title=Комментарий::удалите"
        self.assertEqual(expected, gate.annotation(path, line, message), "аннотация указывает файл и строку")


if __name__ == "__main__":
    unittest.main()
