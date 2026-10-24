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


if __name__ == "__main__":
    unittest.main()
