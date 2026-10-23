#!/usr/bin/env python3
"""Списки «Рейтинг» (IMDb) и «Оскар» (Wikidata) для клиента rezka.

    python3 tools/build_lists.py --cookies rezka-cookies.txt            # всё
    python3 tools/build_lists.py --cookies rezka-cookies.txt --only oscars
    python3 tools/build_lists.py --offline                              # только из кэша, без сети

Пишет app/src/main/assets/lists/top.json (в git не попадает: данные IMDb нельзя распространять)
и app/src/main/assets/lists/oscars.json (Wikidata, CC0).

--cookies — cookies залогиненного аккаунта rezka в формате Netscape (экспорт из браузера или curl -c):
без входа сайт не отдаёт ни поиск, ни страницы.

Кэш (~/.cache/rezka-tv) переживает перезапуски: наборы данных IMDb, ответ Wikidata и все страницы rezka
сохраняются, поэтому прерванный запуск продолжается с того же места, а повторный не ходит в сеть.
К rezka — не чаще одного запроса в 2 секунды, по одному.
"""
from __future__ import annotations

import argparse
import base64
import difflib
import gzip
import hashlib
import http.cookiejar
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import date
from html import unescape
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets" / "lists"
DEFAULT_CACHE = Path.home() / ".cache" / "rezka-tv"

IMDB_DATASETS = "https://datasets.imdbws.com/"
IMDB_MAX_AGE_DAYS = 30
WDQS = "https://query.wikidata.org/sparql"
REZKA = "https://rezka.fi/"
BROWSER_UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
BOT_UA = "RezkaTvListsBuilder/1.0 (personal Android TV app; tools/build_lists.py)"
REZKA_INTERVAL_S = 2.0

BUCKET_SIZE = 500
BUCKETS = ("films", "series", "cartoons", "anime")
# Раздел в адресе rezka → список
CATEGORY_BUCKET = {"films": "films", "series": "series", "cartoons": "cartoons", "animation": "anime"}

SERIES_TYPES = {"tvSeries", "tvMiniSeries"}


@dataclass
class Group:
    """Кандидаты одного вида; min_votes — порог и одновременно m в байесовской формуле."""
    name: str
    types: set
    animated: bool
    min_votes: int
    # Списки, ради которых перебираем группу: когда они заполнены, группа больше не нужна
    fills: tuple


# Пороги подобраны так, чтобы в каждый список попадали известные тайтлы, а мультфильмов и аниме набиралось
# на несколько сотен (с порогом фильмов их было бы втрое меньше)
GROUPS = (
    Group("movies", {"movie"}, False, 25_000, ("films",)),
    Group("series", SERIES_TYPES, False, 10_000, ("series",)),
    Group("anim_movies", {"movie"}, True, 10_000, ("cartoons", "anime")),
    Group("anim_series", SERIES_TYPES, True, 5_000, ("cartoons", "anime")),
)

GENRES_RU = {
    "Action": "Боевик", "Adventure": "Приключения", "Animation": "Анимация", "Biography": "Биография",
    "Comedy": "Комедия", "Crime": "Криминал", "Documentary": "Документальный", "Drama": "Драма",
    "Family": "Семейный", "Fantasy": "Фэнтези", "Film-Noir": "Нуар", "History": "История", "Horror": "Ужасы",
    "Music": "Музыка", "Musical": "Мюзикл", "Mystery": "Детектив", "Romance": "Мелодрама",
    "Sci-Fi": "Фантастика", "Sport": "Спорт", "Thriller": "Триллер", "War": "Военный", "Western": "Вестерн",
    "Game-Show": "Игровое шоу", "Reality-TV": "Реалити-шоу", "Talk-Show": "Ток-шоу", "News": "Новости",
}

# Награды на Wikidata: лучший фильм, анимационный, международный (до 2020 — «на иностранном языке»)
OSCAR_AWARDS = {"Q102427": "picture", "Q106800": "animated", "Q105304": "international"}


def log(*args):
    print(*args, file=sys.stderr, flush=True)


# ---------- Чистые функции (покрыты tools/test_build_lists.py) ----------

def weighted_rating(rating: float, votes: int, m: int, mean: float) -> float:
    """Байесовская оценка IMDb: у тайтлов с малым числом голосов рейтинг подтягивается к среднему."""
    return votes / (votes + m) * rating + m / (votes + m) * mean


def normalize_title(title: str) -> str:
    """Для сравнения названий: регистр, диакритика, пунктуация, «&», артикль в начале."""
    s = unicodedata.normalize("NFKD", title or "")
    s = "".join(ch for ch in s if not unicodedata.combining(ch)).casefold()
    s = s.replace("&", " and ").replace("’", "").replace("'", "")
    s = re.sub(r"[^\w]+", " ", s).strip()
    s = re.sub(r"^(the|a|an) ", "", s)
    return re.sub(r"\s+", " ", s)


def ascii_fold(title: str) -> str:
    s = unicodedata.normalize("NFKD", title)
    return "".join(ch for ch in s if not unicodedata.combining(ch))


def first_year(text: str):
    m = re.search(r"\b(18|19|20)\d{2}\b", text or "")
    return int(m.group(0)) if m else None


def year_ok(card_year, year, tolerance: int = 1) -> bool:
    return card_year is not None and year is not None and abs(card_year - year) <= tolerance


def url_category(url: str):
    """«https://rezka.fi/films/drama/1167-….html» → «films»"""
    path = urllib.parse.urlparse(url).path.strip("/").split("/")
    return path[0] if path and path[0] in CATEGORY_BUCKET else None


def kind_ok(kind: str, category) -> bool:
    """Фильм не может оказаться сериалом и наоборот; мультфильмы и аниме бывают и тем и другим."""
    if category is None:
        return False
    if kind == "movie":
        return category != "series"
    return category != "films"


def similar_title(expected: str, rezka_title: str) -> bool:
    """Похоже ли название на rezka («Крёстный отец / The Godfather») на ожидаемое русское."""
    expected = normalize_title(expected)
    return any(difflib.SequenceMatcher(None, expected, normalize_title(p)).ratio() >= 0.5
               for p in rezka_title.split(" / "))


def wikidata_label(label):
    """«Ярмарка штата (фильм, 1933)» → «Ярмарка штата»: уточнение в скобках только мешает поиску."""
    return re.sub(r"\s*\((?=[^)]*(?:фильм|film|\b(?:18|19|20)\d\d\b))[^)]*\)$", "", label) if label else label


def titles_match(rezka_orig, titles) -> bool:
    if not rezka_orig:
        return False
    variants = {normalize_title(p) for p in rezka_orig.split(" / ")}
    return any(normalize_title(t) in variants for t in titles if t)


def search_queries(*titles) -> list:
    """Что искать: сначала сами названия (английское, оригинальное, русское), потом без диакритики
    и без подзаголовка («Léon: The Professional» → «Léon»)."""
    names = [t.strip() for t in titles if t and t.strip()]
    out = []
    for q in names + [ascii_fold(t) for t in names] + [t.split(":")[0].strip() for t in names]:
        if len(q) >= 2 and q not in out:
            out.append(q)
    return out[:4]


def parse_search(html: str) -> list:
    """Карточки поиска rezka: id, адрес, название, постер, «2010, США, Фантастика»."""
    cards = []
    for block in html.split('class="b-content__inline_item"')[1:]:
        m_id = re.search(r'data-id="(\d+)"', block)
        m_url = re.search(r'data-url="([^"]+)"', block)
        m_link = re.search(r'b-content__inline_item-link">\s*<a [^>]*>(.*?)</a>\s*<div>(.*?)</div>', block, re.S)
        if not (m_id and m_url and m_link):
            continue
        m_img = re.search(r'<img src="([^"]+)"', block)
        url = unescape(m_url.group(1))
        sub = unescape(strip_tags(m_link.group(2)))
        cards.append({
            "id": m_id.group(1), "url": url, "title": unescape(strip_tags(m_link.group(1))),
            "poster": unescape(m_img.group(1)) if m_img else "", "sub": sub,
            "year": first_year(sub), "category": url_category(url),
        })
    return cards


def parse_title_page(html: str) -> dict:
    """IMDb id из ссылки рейтинга (/help/<base64 адреса>/) и оригинальное название."""
    imdb = None
    m = re.search(r'b-post__info_rates imdb"><a href="/help/([^"/]+)/', html)
    if m:
        try:
            target = urllib.parse.unquote(base64.b64decode(m.group(1) + "==").decode("utf-8", "replace"))
            t = re.search(r"tt\d+", target)
            imdb = t.group(0) if t else None
        except ValueError:
            pass
    m = re.search(r'class="b-post__origtitle"[^>]*>([^<]*)<', html)
    return {"imdb": imdb, "orig": unescape(m.group(1)).strip() if m else None}


def strip_tags(s: str) -> str:
    return re.sub(r"<[^>]+>", "", s).strip()


def ceremony_number(year: int) -> int:
    """Номер церемонии по году её проведения; в 1930 их было две — берём первую (весеннюю)."""
    if year <= 1930:
        return year - 1928
    if year <= 1932:
        return year - 1927
    if year <= 1934:
        return 6
    return year - 1928


def ceremony_year(n: int) -> int:
    return {1: 1929, 2: 1930, 3: 1930, 4: 1931, 5: 1932, 6: 1934}.get(n, n + 1928)


def ceremony_films(n: int) -> str:
    """За какие годы фильмы: до 6-й церемонии сезон шёл с августа по июль."""
    return f"{1926 + n}–{1927 + n}" if n <= 6 else str(1927 + n)


# ---------- Сеть с кэшем ----------

class Http:
    def __init__(self, cache: Path, cookies, offline: bool):
        self.cache = cache / "rezka"
        self.offline = offline
        self.last = 0.0
        self.requests = 0
        self.opener = None
        if cookies:
            jar = http.cookiejar.MozillaCookieJar(str(cookies))
            jar.load(ignore_discard=True, ignore_expires=True)
            self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def _path(self, url: str) -> Path:
        h = hashlib.sha1(url.encode()).hexdigest()
        return self.cache / h[:2] / (h + ".html.gz")

    def get(self, url: str):
        """Страница rezka; None — нет в кэше в режиме offline."""
        path = self._path(url)
        if path.exists():
            return gzip.decompress(path.read_bytes()).decode("utf-8")
        if self.offline:
            return None
        if self.opener is None:
            sys.exit("Нужны cookies rezka: --cookies <файл>")
        for attempt in range(4):
            wait = self.last + REZKA_INTERVAL_S - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            self.last = time.monotonic()
            self.requests += 1
            try:
                req = urllib.request.Request(url, headers={"User-Agent": BROWSER_UA, "Accept-Language": "ru,en;q=0.8"})
                with self.opener.open(req, timeout=40) as resp:
                    body = resp.read()
                break
            except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
                code = getattr(e, "code", None)
                if code == 404:
                    body = b""
                    break
                log(f"  ! {e} — повтор через {30 * (attempt + 1)} с")
                time.sleep(30 * (attempt + 1))
        else:
            raise RuntimeError(f"rezka не отвечает: {url}")
        # Сайт отдаёт gzip, даже если его не просили
        if body[:2] == b"\x1f\x8b":
            body = gzip.decompress(body)
        html = body.decode("utf-8", "replace")
        if 'id="loginForm"' in html and "b-content__inline_item" not in html and "b-post__title" not in html:
            sys.exit("rezka просит войти: cookies устарели, выгрузите их заново")
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_bytes(gzip.compress(html.encode("utf-8")))
        tmp.replace(path)
        return html


def download(url: str, path: Path, max_age_days: int):
    if path.exists() and time.time() - path.stat().st_mtime < max_age_days * 86400:
        return
    log(f"Скачиваю {url}")
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".part")
    req = urllib.request.Request(url, headers={"User-Agent": BOT_UA})
    with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as out:
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    tmp.replace(path)


# ---------- Сопоставление с rezka ----------

class Matcher:
    def __init__(self, http_: Http):
        self.http = http_

    def search(self, query: str):
        html = self.http.get(REZKA + "search/?do=search&subaction=search&q=" + urllib.parse.quote_plus(query))
        return None if html is None else parse_search(html)

    def title_page(self, url: str):
        html = self.http.get(url)
        return None if html is None else parse_title_page(html)

    def match(self, imdb, kind: str, year, titles: list, russian=None):
        """Карточка rezka для тайтла или None. kind — movie/series, titles — варианты названия для сверки,
        russian — русское название (из Wikidata), если известно."""
        for query in search_queries(*titles, russian):
            cards = self.search(query)
            if cards is None:
                return None
            found = [c for c in cards if year_ok(c["year"], year) and kind_ok(kind, c["category"])]
            if not found:
                continue
            # Единственный подходящий по году и виду, и он первый в выдаче (а русское название, если известно,
            # похоже) — без проверки страницы; иначе сверяемся с IMDb на странице тайтла
            single = found[0] if len(found) == 1 and cards[0] is found[0] else None
            if single and (not russian or similar_title(russian, single["title"])):
                return single
            for card in found[:3]:
                page = self.title_page(card["url"])
                if page is None:
                    return None
                if imdb and page["imdb"] == imdb:
                    return card
                if (not imdb or not page["imdb"]) and titles_match(page["orig"], titles):
                    return card
        return None


def rezka_entry(card: dict) -> dict:
    """Ссылка без домена: в приложении она дополняется текущим зеркалом."""
    return {
        "id": card["id"],
        "url": urllib.parse.urlparse(card["url"]).path,
        "title": card["title"],
        "poster": card["poster"],
    }


# ---------- IMDb ----------

def load_imdb(cache: Path, min_votes: int, wanted: set) -> dict:
    """tconst → тайтл (фильмы и сериалы, не для взрослых) с рейтингом; редкие — только из wanted."""
    d = cache / "imdb"
    for name in ("title.basics.tsv.gz", "title.ratings.tsv.gz"):
        download(IMDB_DATASETS + name, d / name, IMDB_MAX_AGE_DAYS)
    ratings = {}
    with gzip.open(d / "title.ratings.tsv.gz", "rt", encoding="utf-8") as f:
        next(f)
        for line in f:
            tconst, rating, votes = line.rstrip("\n").split("\t")
            votes = int(votes)
            if votes >= min_votes or tconst in wanted:
                ratings[tconst] = (float(rating), votes)
    titles = {}
    with gzip.open(d / "title.basics.tsv.gz", "rt", encoding="utf-8") as f:
        next(f)
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[0] not in wanted and (p[0] not in ratings or p[4] != "0"):
                continue
            if p[1] != "movie" and p[1] not in SERIES_TYPES and p[0] not in wanted:
                continue
            rating, votes = ratings.get(p[0], (0.0, 0))
            titles[p[0]] = {
                "imdb": p[0], "type": p[1], "title": p[2], "orig": p[3],
                "year": int(p[5]) if p[5].isdigit() else None,
                "genres": [] if p[8] == "\\N" else p[8].split(","),
                "rating": rating, "votes": votes,
            }
    return titles


def candidates(titles: dict) -> dict:
    """Группа → кандидаты по убыванию взвешенного рейтинга."""
    out = {}
    for g in GROUPS:
        items = [t for t in titles.values()
                 if t["type"] in g.types and ("Animation" in t["genres"]) == g.animated and t["votes"] >= g.min_votes]
        if not items:
            out[g.name] = []
            continue
        mean = sum(t["rating"] for t in items) / len(items)
        for t in items:
            t["score"] = weighted_rating(t["rating"], t["votes"], g.min_votes, mean)
        out[g.name] = sorted(items, key=lambda t: -t["score"])
    return out


def build_top(matcher: Matcher, titles: dict, out: Path, sample):
    groups = candidates(titles)
    for g in GROUPS:
        log(f"Кандидатов {g.name}: {len(groups[g.name])} (от {g.min_votes} голосов)")
    buckets = {b: {} for b in BUCKETS}
    unmatched = []
    seen = {g.name: 0 for g in GROUPS}
    queues = {g.name: list(groups[g.name][:sample] if sample else groups[g.name]) for g in GROUPS}
    processed = 0

    def full(names):
        return all(len(buckets[b]) >= BUCKET_SIZE for b in names)

    # По очереди из каждой группы: прерванный запуск даёт ровные, пусть и короткие списки
    while True:
        progressed = False
        for g in GROUPS:
            q = queues[g.name]
            if not q or full(g.fills):
                continue
            item = q.pop(0)
            seen[g.name] += 1
            progressed = True
            kind = "movie" if item["type"] == "movie" else "series"
            card = matcher.match(item["imdb"], kind, item["year"], [item["title"], item["orig"]])
            if card is None:
                unmatched.append((g.name, item))
            else:
                bucket = CATEGORY_BUCKET[card["category"]]
                prev = buckets[bucket].get(card["id"])
                if prev is None or prev["score"] < item["score"]:
                    buckets[bucket][card["id"]] = {**item, "rezka": rezka_entry(card)}
            processed += 1
            if processed % 25 == 0:
                log(f"  {processed}: " + ", ".join(f"{b} {len(buckets[b])}" for b in BUCKETS)
                    + f"; не найдено {len(unmatched)}; запросов {matcher.http.requests}")
            if processed % 100 == 0:
                write_top(buckets, out)
        if not progressed:
            break
    write_top(buckets, out)
    log("Рейтинг: " + ", ".join(f"{b} {min(len(buckets[b]), BUCKET_SIZE)}" for b in BUCKETS))
    for g in GROUPS:
        miss = sum(1 for name, _ in unmatched if name == g.name)
        log(f"  {g.name}: проверено {seen[g.name]}, не найдено на rezka {miss}")
    return unmatched


def write_top(buckets: dict, out: Path):
    data = {"source": "IMDb non-commercial datasets — только для личного использования",
            "generated": date.today().isoformat(), "buckets": {}}
    for b in BUCKETS:
        items = sorted(buckets[b].values(), key=lambda t: -t["score"])[:BUCKET_SIZE]
        data["buckets"][b] = [{
            "rank": i + 1, "imdb": t["imdb"], "rating": t["rating"], "votes": t["votes"], "year": t["year"],
            "genres": [GENRES_RU[g] for g in t["genres"] if g in GENRES_RU and g != "Animation"],
            "orig": t["title"], **t["rezka"],
        } for i, t in enumerate(items)]
    write_json(out, data)


# ---------- Оскар ----------

OSCAR_QUERY = """
SELECT ?award ?film ?ru ?en ?imdb ?won ?time ?ed ?pub WHERE {
  VALUES ?award { %s }
  { ?film p:P166 ?st . ?st ps:P166 ?award . BIND(1 AS ?won) }
  UNION
  { ?film p:P1411 ?st . ?st ps:P1411 ?award . BIND(0 AS ?won) }
  FILTER NOT EXISTS { ?film wdt:P31 wd:Q5 }
  OPTIONAL { ?st pq:P585 ?time }
  OPTIONAL { ?st pq:P805 ?cer . ?cer wdt:P393 ?ed }
  OPTIONAL { ?film wdt:P345 ?imdb }
  OPTIONAL { ?film wdt:P577 ?pub }
  OPTIONAL { ?film rdfs:label ?ru FILTER(LANG(?ru) = "ru") }
  OPTIONAL { ?film rdfs:label ?en FILTER(LANG(?en) = "en") }
}
"""


def fetch_oscars(cache: Path, offline: bool, refresh: bool) -> list:
    path = cache / "wikidata" / "oscars.json"
    if path.exists() and (offline or not refresh):
        return json.loads(path.read_text())
    if offline:
        sys.exit("Нет ответа Wikidata в кэше: запустите без --offline")
    query = OSCAR_QUERY % " ".join("wd:" + q for q in OSCAR_AWARDS)
    url = WDQS + "?format=json&query=" + urllib.parse.quote(query)
    # Без Accept-Encoding: gzip служба запросов сейчас режет клиента до запроса в минуту
    req = urllib.request.Request(url, headers={
        "User-Agent": BOT_UA, "Accept": "application/sparql-results+json", "Accept-Encoding": "gzip"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                body = resp.read()
            if body[:2] == b"\x1f\x8b":
                body = gzip.decompress(body)
            rows = json.loads(body)["results"]["bindings"]
            break
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 4:
                raise
            log("  Wikidata: 429, жду минуту")
            time.sleep(65)
    rows = [{k: v["value"] for k, v in r.items()} for r in rows]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(rows, ensure_ascii=False))
    return rows


def oscar_nominations(rows: list) -> dict:
    """(церемония, категория) → {qid: номинант}; победа перекрывает номинацию."""
    first_pub = {}
    for r in rows:
        pub = first_year(r.get("pub", "")[:4])
        if pub and pub < first_pub.get(r["film"], 9999):
            first_pub[r["film"]] = pub
    out = {}
    for r in rows:
        category = OSCAR_AWARDS[r["award"].rsplit("/", 1)[1]]
        qid = r["film"].rsplit("/", 1)[1]
        n = int(r["ed"]) if r.get("ed", "").isdigit() else None
        if n is None and r.get("time"):
            year = int(r["time"][:4])
            # Дата без церемонии: полная — это церемония или объявление номинантов, а «1 января» года выхода
            # фильма — обычно год фильма, награждают за него на следующий год
            film_year = r["time"][5:10] == "01-01" and first_pub.get(r["film"]) == year
            n = ceremony_number(year + 1 if film_year else year)
        if n is None or n < 1:
            continue
        film = out.setdefault((n, category), {}).setdefault(qid, {
            "qid": qid, "imdb": None, "ru": None, "en": None, "year": first_pub.get(r["film"]), "won": False})
        film["won"] = film["won"] or r["won"] == "1"
        film["imdb"] = film["imdb"] or (r.get("imdb") if re.fullmatch(r"tt\d+", r.get("imdb", "")) else None)
        film["ru"] = film["ru"] or wikidata_label(r.get("ru"))
        film["en"] = film["en"] or wikidata_label(r.get("en"))
    return out


def load_fixes(path: Path) -> list:
    """Строки oscars_fixes.tsv: (действие, церемония, категория, название, год или None)."""
    fixes = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        action, n, category, title, *year = line.split("\t")
        fixes.append((action, int(n), category, title, int(year[0]) if year and year[0] else None))
    return fixes


def apply_fixes(noms: dict, fixes: list, titles: dict):
    """Дополнить и почистить номинантов; добавленные ищутся в IMDb по названию и году."""
    index = {}
    for t in titles.values():
        if t["type"] == "movie":
            for name in {normalize_title(t["title"]), normalize_title(t["orig"])}:
                index.setdefault(name, []).append(t)
    for action, n, category, title, year in fixes:
        films = noms.setdefault((n, category), {})
        key = normalize_title(title)
        if action == "-":
            for qid, f in list(films.items()):
                t = titles.get(f["imdb"]) if f["imdb"] else None
                names = {normalize_title(x) for x in (f["en"], t and t["title"]) if x}
                if key in names and (year is None or f["year"] == year):
                    del films[qid]
            continue
        found = [t for t in index.get(key, []) if year_ok(t["year"], year)]
        if not found:
            log(f"  ! oscars_fixes: нет в IMDb «{title}» ({year})")
            continue
        t = max(found, key=lambda x: x["votes"])
        film = next((f for f in films.values() if f["imdb"] == t["imdb"]), None)
        if film is None:
            film = films[t["imdb"]] = {"qid": t["imdb"], "imdb": t["imdb"], "ru": None, "en": t["title"],
                                       "year": t["year"], "won": False}
        film["won"] = film["won"] or action == "*"


def build_oscars(matcher: Matcher, rows: list, titles: dict, fixes: list, out: Path):
    noms = oscar_nominations(rows)
    apply_fixes(noms, fixes, titles)
    found = missing = 0
    cache = {}
    ceremonies = {}
    for (n, category), films in sorted(noms.items(), key=lambda kv: (-kv[0][0], kv[0][1])):
        entries = {}
        for f in films.values():
            t = titles.get(f["imdb"]) if f["imdb"] else None
            orig = t["title"] if t else (f["en"] or f["ru"] or "")
            year = (t["year"] if t and t["year"] else f["year"]) or int(ceremony_films(n)[:4])
            key = f["imdb"] or f["qid"]
            if key in entries:  # два элемента Wikidata про один фильм
                entries[key]["won"] = entries[key]["won"] or f["won"]
                continue
            if key not in cache:
                names = [t["title"], t["orig"]] if t else [f["en"]]
                cache[key] = matcher.match(f["imdb"], "movie", year, names, russian=f["ru"])
            card = cache[key]
            entry = {"imdb": f["imdb"], "title": f["ru"] or orig, "orig": orig, "year": year, "won": f["won"]}
            if card:
                entry.update(rezka_entry(card))
                entry["title"] = card["title"]
                found += 1
            else:
                missing += 1
            entries[key] = entry
        if not entries:
            continue
        # Победитель первым, дальше по алфавиту
        ceremonies.setdefault(n, {})[category] = sorted(entries.values(), key=lambda e: (not e["won"], e["title"]))
        if category == "picture" and n % 10 == 0:
            log(f"  Оскар: до церемонии {n}: найдено {found}, нет на rezka {missing}, запросов {matcher.http.requests}")
    data = {
        "source": "Wikidata (CC0)",
        "generated": date.today().isoformat(),
        "ceremonies": [{
            "n": n, "year": ceremony_year(n), "films": ceremony_films(n),
            "nominees": {c: cats[c] for c in OSCAR_AWARDS.values() if c in cats},
        } for n, cats in sorted(ceremonies.items(), reverse=True)],
    }
    write_json(out, data)
    for c in OSCAR_AWARDS.values():
        items = [e for cats in ceremonies.values() for e in cats.get(c, [])]
        log(f"Оскар, {c}: церемоний {sum(1 for cats in ceremonies.values() if c in cats)}, номинаций {len(items)}, "
            f"нет на rezka {sum(1 for e in items if 'id' not in e)}")
    return [(k, v) for k, v in cache.items() if v is None]


# ---------- Вывод ----------

def to_json(v, depth: int = 0) -> str:
    """JSON, где каждый тайтл — одна строка: читаемо в diff и без лишних отступов."""
    def nested(x):
        return isinstance(x, dict) or (isinstance(x, list) and any(isinstance(y, (dict, list)) for y in x))
    pad = " " * (depth + 1)
    if isinstance(v, dict) and any(nested(x) for x in v.values()):
        inner = ",\n".join(pad + json.dumps(k, ensure_ascii=False) + ": " + to_json(x, depth + 1) for k, x in v.items())
        return "{\n" + inner + "\n" + " " * depth + "}"
    if isinstance(v, list) and any(isinstance(x, (dict, list)) for x in v):
        return "[\n" + ",\n".join(pad + to_json(x, depth + 1) for x in v) + "\n" + " " * depth + "]"
    return json.dumps(v, ensure_ascii=False, separators=(",", ":"))


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(to_json(data) + "\n", encoding="utf-8")
    tmp.replace(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cookies", type=Path, default=os.environ.get("REZKA_COOKIES"),
                    help="cookies rezka в формате Netscape (или переменная REZKA_COOKIES)")
    ap.add_argument("--cache", type=Path, default=DEFAULT_CACHE, help=f"кэш (по умолчанию {DEFAULT_CACHE})")
    ap.add_argument("--only", choices=("top", "oscars"), help="собрать только один список")
    ap.add_argument("--offline", action="store_true", help="не ходить в сеть, только кэш")
    ap.add_argument("--refresh-oscars", action="store_true", help="заново запросить Wikidata")
    ap.add_argument("--sample", type=int, help="только первые N кандидатов каждой группы (проба)")
    ap.add_argument("--out", type=Path, default=ASSETS, help="куда писать JSON")
    args = ap.parse_args()

    http_ = Http(args.cache, args.cookies, args.offline)
    matcher = Matcher(http_)
    rows = fetch_oscars(args.cache, args.offline, args.refresh_oscars) if args.only != "top" else []
    wanted = {r["imdb"] for r in rows if re.fullmatch(r"tt\d+", r.get("imdb", ""))}
    # Порог ниже, чем у списков: номинантов из oscars_fixes.tsv ищем в IMDb по названию
    titles = load_imdb(args.cache, 1000, wanted)
    log(f"IMDb: {len(titles)} тайтлов")

    report = []
    if args.only != "top":
        fixes = load_fixes(Path(__file__).resolve().parent / "oscars_fixes.tsv")
        for key, _ in build_oscars(matcher, rows, titles, fixes, args.out / "oscars.json"):
            report.append(("oscar", key, titles.get(key, {}).get("title", "")))
    if args.only != "oscars":
        for group, item in build_top(matcher, titles, args.out / "top.json", args.sample):
            report.append((group, item["imdb"], f'{item["title"]} ({item["year"]})'))
    path = args.cache / "unmatched.tsv"
    path.write_text("".join("\t".join(map(str, r)) + "\n" for r in report), encoding="utf-8")
    log(f"Не найдено на rezka: {len(report)} (список: {path}); запросов к rezka: {http_.requests}")


if __name__ == "__main__":
    main()
