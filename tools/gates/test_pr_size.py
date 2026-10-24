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
        parsed = parse_numstat("3\t4\tapp/A.kt\0")
        self.assertEqual([Change("app/A.kt", 7)], parsed, "строки — сумма добавленных и удалённых")

    def test_counts_binary_file_without_lines(self):
        parsed = parse_numstat("-\t-\tapp/src/main/res/drawable/logo.png\0")
        self.assertEqual(0, parsed[0].lines, "двоичный файл засчитывается без строк")

    def test_reads_target_of_rename(self):
        parsed = parse_numstat("0\t0\t\0app/old/Parser.kt\0app/core/Parser.kt\0")
        self.assertEqual("app/core/Parser.kt", parsed[0].path, "переименование засчитывается по новому пути")

    def test_reads_record_after_rename(self):
        parsed = parse_numstat("0\t0\t\0Old.kt\0New.kt\0" "2\t5\tNext.kt\0")
        self.assertEqual(Change("Next.kt", 7), parsed[1], "запись после переименования читается целиком")

    def test_counts_file_whose_name_only_looks_like_rename(self):
        disguised = "app/src/main/Ж => quality/lint-baseline.xml"
        row = f"211\t0\t{disguised}\0"
        self.assertEqual(1, len(violations(parse_numstat(row))), "имя с « => » не превращает файл в журнал долга")


if __name__ == "__main__":
    unittest.main()
