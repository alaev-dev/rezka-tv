import tempfile
import unittest
from pathlib import Path

from gates import comments as gate


class CommentsTest(unittest.TestCase):

    def test_finds_line_comment_in_kotlin(self):
        source = 'val ёж = 7\n// пояснение\n'
        expected = [2]
        self.assertEqual(expected, gate.comment_lines("A.kt", source), "строчный комментарий Kotlin найден")

    def test_finds_trailing_block_comment_in_kotlin(self):
        source = "fun f() = 3 /* три */\n"
        expected = [1]
        self.assertEqual(expected, gate.comment_lines("B.kts", source), "блочный комментарий в конце строки найден")

    def test_ignores_slashes_inside_kotlin_string(self):
        source = 'val url = "https://rezka.test/films/"\n'
        expected = []
        self.assertEqual(expected, gate.comment_lines("C.kt", source), "слеши в строке — не комментарий")

    def test_ignores_slashes_inside_raw_string(self):
        source = 'val js = """\n  a // b\n"""\n'
        expected = []
        self.assertEqual(expected, gate.comment_lines("D.kt", source), "слеши в сырой строке — не комментарий")

    def test_counts_lines_after_multiline_string(self):
        source = 'val s = """\nx\ny\n"""\n// после\n'
        expected = [5]
        self.assertEqual(expected, gate.comment_lines("E.kt", source), "номер строки учитывает многострочную строку")

    def test_finds_xml_comment(self):
        source = '<a>\n  <!-- старое -->\n</a>\n'
        expected = [2]
        self.assertEqual(expected, gate.comment_lines("res/layout/f.xml", source), "XML-комментарий найден")

    def test_finds_hash_comment_after_yaml_value(self):
        source = "on:\n  push: main # ветка\n"
        expected = [2]
        self.assertEqual(expected, gate.comment_lines(".github/workflows/g.yml", source), "комментарий YAML найден")

    def test_ignores_hash_inside_quotes(self):
        source = 'color: "#B60205"\n'
        expected = []
        self.assertEqual(expected, gate.comment_lines("h.yml", source), "решётка в кавычках — не комментарий")

    def test_skips_unknown_file_types(self):
        source = "// не код\n"
        expected = []
        self.assertEqual(expected, gate.comment_lines("README.md", source), "документация не проверяется")

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
