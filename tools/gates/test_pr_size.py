import unittest

from gates.pr_size import Change, parse_numstat, violations


def changes(count: int, lines: int, folder: str = "app/src/main") -> list:
    return [Change(f"{folder}/F{i}.kt", lines) for i in range(count)]


class PrSizeTest(unittest.TestCase):

    def test_accepts_change_at_line_limit(self):
        self.assertEqual([], violations(changes(2, 100)), "200 строк — ещё в пределе")

    def test_rejects_change_over_line_limit(self):
        self.assertEqual(1, len(violations(changes(1, 201))), "201 строка должна блокировать PR")

    def test_rejects_change_over_file_limit(self):
        self.assertEqual(1, len(violations(changes(11, 1))), "11 файлов должны блокировать PR")

    def test_ignores_test_resources(self):
        resources = changes(20, 500, "app/src/test/resources/rezka")
        self.assertEqual([], violations(resources), "фикстуры не входят в размер PR")

    def test_ignores_quality_baselines(self):
        baseline = [Change("quality/lint-baseline.xml", 900)]
        self.assertEqual([], violations(baseline), "журнал долга не входит в размер PR")

    def test_counts_added_and_deleted_lines(self):
        parsed = parse_numstat("3\t4\tapp/A.kt\n")
        self.assertEqual([Change("app/A.kt", 7)], parsed, "строки — сумма добавленных и удалённых")

    def test_counts_binary_file_without_lines(self):
        parsed = parse_numstat("-\t-\tapp/src/main/res/drawable/logo.png\n")
        self.assertEqual(0, parsed[0].lines, "двоичный файл засчитывается без строк")

    def test_reads_target_of_braced_rename(self):
        parsed = parse_numstat("0\t0\tapp/{old => core}/Parser.kt\n")
        self.assertEqual("app/core/Parser.kt", parsed[0].path, "переименование засчитывается по новому пути")

    def test_reads_target_of_rename_into_root(self):
        parsed = parse_numstat("0\t0\tapp/{old => }/Parser.kt\n")
        self.assertEqual("app/Parser.kt", parsed[0].path, "перенос в корень не оставляет двойного слеша")

    def test_reads_target_of_plain_rename(self):
        parsed = parse_numstat("1\t0\tA.kt => B.kt\n")
        self.assertEqual("B.kt", parsed[0].path, "простое переименование засчитывается по новому пути")


if __name__ == "__main__":
    unittest.main()
