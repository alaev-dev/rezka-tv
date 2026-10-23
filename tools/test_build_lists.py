"""Тесты чистых функций build_lists.py: python3 -m unittest discover -s tools"""
import base64
import unittest
import urllib.parse

import build_lists as b


class WeightedRatingTest(unittest.TestCase):

    def test_few_votes_pulled_to_mean(self):
        many = b.weighted_rating(9.0, 2_000_000, 25_000, 7.0)
        few = b.weighted_rating(9.0, 25_000, 25_000, 7.0)
        self.assertAlmostEqual(8.0, few)  # голосов ровно m — середина между оценкой и средним
        self.assertGreater(many, few)
        self.assertAlmostEqual(8.975, many, places=2)

    def test_ranking_prefers_confident_score(self):
        popular = b.weighted_rating(8.6, 1_500_000, 25_000, 6.9)
        niche = b.weighted_rating(9.4, 30_000, 25_000, 6.9)
        self.assertGreater(popular, niche)


class TitleTest(unittest.TestCase):

    def test_normalize(self):
        self.assertEqual("amelie", b.normalize_title("Amélie"))
        self.assertEqual("leon the professional", b.normalize_title("Léon: The Professional"))
        self.assertEqual(b.normalize_title("The Lord of the Rings"), b.normalize_title("Lord of the Rings"))
        self.assertEqual(b.normalize_title("Fast & Furious"), b.normalize_title("Fast and Furious"))
        self.assertEqual("schindlers list", b.normalize_title("Schindler's List"))
        self.assertEqual("wall e", b.normalize_title("WALL·E"))

    def test_titles_match_alternatives(self):
        self.assertTrue(b.titles_match("Léon / Leon: The Professional", ["Léon: The Professional", "Léon"]))
        self.assertTrue(b.titles_match("The Godfather", ["Godfather"]))
        self.assertFalse(b.titles_match("The Godfather Part II", ["The Godfather"]))
        self.assertFalse(b.titles_match(None, ["Heat"]))

    def test_search_queries(self):
        self.assertEqual(["Léon: The Professional", "Léon", "Leon: The Professional", "Leon"],
                         b.search_queries("Léon: The Professional", "Léon"))
        self.assertEqual(["Heat"], b.search_queries("Heat", "Heat", None))
        # Русское название из Wikidata не вытесняется вариантами английского
        self.assertEqual(["Amélie", "Le fabuleux destin d'Amélie Poulain", "Амели", "Amelie"],
                         b.search_queries("Amélie", "Le fabuleux destin d'Amélie Poulain", "Амели"))


class YearAndKindTest(unittest.TestCase):

    def test_year_tolerance(self):
        self.assertTrue(b.year_ok(1995, 1995))
        self.assertTrue(b.year_ok(1996, 1995))
        self.assertTrue(b.year_ok(1994, 1995))
        self.assertFalse(b.year_ok(1997, 1995))
        self.assertFalse(b.year_ok(None, 1995))

    def test_first_year(self):
        self.assertEqual(2010, b.first_year("2010, США, Фантастика"))
        self.assertEqual(2024, b.first_year("2024 - ..., США, Документальные"))
        self.assertEqual(1984, b.first_year("1984-1986, США, Приключения"))
        self.assertIsNone(b.first_year("США"))

    def test_kind(self):
        self.assertEqual("films", b.url_category("https://rezka.fi/films/drama/1167-krestnyy-otec-1972-latest.html"))
        self.assertEqual("animation", b.url_category("https://rezka.fi/animation/adventures/829-x.html"))
        self.assertIsNone(b.url_category("https://rezka.fi/collections/1-x.html"))
        self.assertTrue(b.kind_ok("movie", "cartoons"))
        self.assertFalse(b.kind_ok("movie", "series"))
        self.assertTrue(b.kind_ok("series", "animation"))
        self.assertFalse(b.kind_ok("series", "films"))


SEARCH = """
<div class="b-content__inline_items">
<div class="b-content__inline_item" data-id="1076" data-url="https://rezka.fi/films/action/1076-shvatka-1995.html">
 <div class="b-content__inline_item-cover"> <a href="https://rezka.fi/films/action/1076-shvatka-1995.html">
 <img src="https://static.example/i/heat.jpg" height="250" width="166" alt="Схватка" /></a></div>
 <div class="b-content__inline_item-link"> <a href="https://rezka.fi/films/action/1076-shvatka-1995.html">Схватка</a> <div>1995, США, Боевики</div> </div></div>
<div class="b-content__inline_item" data-id="59348" data-url="https://rezka.fi/series/drama/59348-nakal-2023.html">
 <div class="b-content__inline_item-cover"><a href="#"><img src="https://static.example/i/n.jpg" /></a></div>
 <div class="b-content__inline_item-link"> <a href="https://rezka.fi/series/drama/59348-nakal-2023.html">Накал &amp; жар</a> <div>2023 - ..., Австралия, Драмы</div> </div></div>
</div>
"""


class ParseTest(unittest.TestCase):

    def test_search_cards(self):
        cards = b.parse_search(SEARCH)
        self.assertEqual(2, len(cards))
        self.assertEqual({"id": "1076", "url": "https://rezka.fi/films/action/1076-shvatka-1995.html", "title": "Схватка",
                          "poster": "https://static.example/i/heat.jpg", "sub": "1995, США, Боевики", "year": 1995,
                          "category": "films"}, cards[0])
        self.assertEqual("Накал & жар", cards[1]["title"])
        self.assertEqual(("series", 2023), (cards[1]["category"], cards[1]["year"]))

    def test_title_page_imdb_link(self):
        link = base64.b64encode(urllib.parse.quote("https://www.imdb.com/title/tt0113277/", safe="").encode()).decode()
        html = ('<div class="b-post__origtitle" itemprop="alternativeHeadline">Heat</div>'
                f'<span class="b-post__info_rates imdb"><a href="/help/{link}/" target="_blank">IMDb</a>: '
                '<span class="bold">8.3</span></span>')
        self.assertEqual({"imdb": "tt0113277", "orig": "Heat"}, b.parse_title_page(html))
        self.assertEqual({"imdb": None, "orig": None}, b.parse_title_page("<div></div>"))


class FakeHttp:
    """Поиск и страницы тайтлов из словаря вместо сети."""

    def __init__(self, pages):
        self.pages = pages
        self.requested = []
        self.requests = 0

    def get(self, url):
        self.requested.append(url)
        return self.pages.get(url, "")


class MatcherTest(unittest.TestCase):

    def search_url(self, q):
        return b.REZKA + "search/?do=search&subaction=search&q=" + urllib.parse.quote_plus(q)

    def test_single_candidate_without_page(self):
        http = FakeHttp({self.search_url("Heat"): SEARCH})
        card = b.Matcher(http).match("tt0113277", "movie", 1995, ["Heat", "Heat"])
        self.assertEqual("1076", card["id"])
        self.assertEqual(1, len(http.requested))

    def test_single_candidate_not_first_is_verified(self):
        # «Схватка» вторая в выдаче: страница проверяется, IMDb не совпал — не берём
        other = SEARCH.replace("series/drama/59348-nakal-2023.html", "films/drama/59348-nakal-1995.html").replace(
            "2023 - ..., Австралия", "1995, США")
        reordered = other.replace('data-id="1076"', 'data-id="X"').replace('data-id="59348"', 'data-id="1076"').replace(
            'data-id="X"', 'data-id="59348"')
        http = FakeHttp({self.search_url("Heat"): reordered})
        card = b.Matcher(http).match("tt0113277", "movie", 1995, ["Heat"])
        self.assertIsNone(card)
        self.assertEqual(3, len(http.requested))  # поиск и две страницы

    def test_russian_title_must_look_alike(self):
        http = FakeHttp({self.search_url("Heat"): SEARCH})
        self.assertIsNone(b.Matcher(http).match("tt0113277", "movie", 1995, ["Heat"], russian="Жара"))
        http = FakeHttp({self.search_url("Heat"): SEARCH})
        self.assertEqual("1076", b.Matcher(http).match("tt0113277", "movie", 1995, ["Heat"], russian="Схватка")["id"])

    def test_wikidata_label(self):
        self.assertEqual("Ярмарка штата", b.wikidata_label("Ярмарка штата (фильм, 1933)"))
        self.assertEqual("Доверие", b.wikidata_label("Доверие (1980)"))
        self.assertEqual("Мальчишки (из «Никеля»)", b.wikidata_label("Мальчишки (из «Никеля»)"))
        self.assertTrue(b.similar_title("Крёстный отец", "Крестный отец"))
        self.assertTrue(b.similar_title("Теория всего", "Вселенная Стивена Хокинга / Теория всего"))
        self.assertFalse(b.similar_title("Ярмарка штата", "Золотоискательницы 1933 года"))

    def test_wrong_year_or_kind_rejected(self):
        http = FakeHttp({self.search_url("Heat"): SEARCH})
        self.assertIsNone(b.Matcher(http).match("tt1", "movie", 1990, ["Heat"]))
        self.assertIsNone(b.Matcher(FakeHttp({self.search_url("Heat"): SEARCH})).match("tt1", "movie", 2023, ["Heat"]))

    def test_ambiguous_resolved_by_imdb_id(self):
        twin = SEARCH.replace('data-id="59348"', 'data-id="50654"').replace(
            "series/drama/59348-nakal-2023.html", "films/thriller/50654-prikrytie-1995.html").replace(
            "2023 - ..., Австралия", "1995, США")
        link = base64.b64encode(urllib.parse.quote("https://www.imdb.com/title/tt0113277/", safe="").encode()).decode()
        other = base64.b64encode(urllib.parse.quote("https://www.imdb.com/title/tt0999999/", safe="").encode()).decode()
        http = FakeHttp({
            self.search_url("Heat"): twin,
            "https://rezka.fi/films/action/1076-shvatka-1995.html": f'b-post__info_rates imdb"><a href="/help/{other}/">',
            "https://rezka.fi/films/thriller/50654-prikrytie-1995.html": f'b-post__info_rates imdb"><a href="/help/{link}/">',
        })
        self.assertEqual("50654", b.Matcher(http).match("tt0113277", "movie", 1995, ["Heat"])["id"])


class OscarTest(unittest.TestCase):

    def test_ceremony_years(self):
        self.assertEqual(97, b.ceremony_number(2025))
        self.assertEqual(2025, b.ceremony_year(97))
        self.assertEqual("2024", b.ceremony_films(97))
        self.assertEqual("1927–1928", b.ceremony_films(1))
        self.assertEqual(1934, b.ceremony_year(6))
        self.assertEqual(7, b.ceremony_number(1935))

    def test_nominations_merge_win_and_nomination(self):
        rows = [
            {"award": "http://www.wikidata.org/entity/Q102427", "film": "http://www.wikidata.org/entity/Q1",
             "won": "0", "ed": "97", "imdb": "tt28607951", "ru": "Анора", "en": "Anora", "pub": "2024-05-21T00:00:00Z"},
            {"award": "http://www.wikidata.org/entity/Q102427", "film": "http://www.wikidata.org/entity/Q1",
             "won": "1", "time": "2025-03-02T00:00:00Z"},
            {"award": "http://www.wikidata.org/entity/Q106800", "film": "http://www.wikidata.org/entity/Q2",
             "won": "0", "time": "2025-01-01T00:00:00Z", "en": "Flow"},
        ]
        noms = b.oscar_nominations(rows)
        anora = noms[(97, "picture")]["Q1"]
        self.assertTrue(anora["won"])
        self.assertEqual(("tt28607951", "Анора", 2024), (anora["imdb"], anora["ru"], anora["year"]))
        self.assertFalse(noms[(97, "animated")]["Q2"]["won"])


class JsonTest(unittest.TestCase):

    def test_one_title_per_line(self):
        text = b.to_json({"a": [{"x": 1, "g": ["Драма"]}, {"x": 2, "g": []}]})
        self.assertEqual('{\n "a": [\n  {"x":1,"g":["Драма"]},\n  {"x":2,"g":[]}\n ]\n}', text)


if __name__ == "__main__":
    unittest.main()
