# Сторонние компоненты

Код из сторонних проектов в репозиторий не копировался: всё ниже подключается как зависимости Gradle
(исключение — данные контуров иконок Material Icons, см. ниже). Тексты лицензий —
в самих артефактах и по ссылкам.

## Библиотеки в приложении

| Компонент | Зачем | Лицензия |
|---|---|---|
| [AndroidX Leanback, Leanback Preference](https://developer.android.com/jetpack/androidx/releases/leanback) | ряды, панель плеера, экран настроек | Apache-2.0 |
| [AndroidX Media3](https://github.com/androidx/media): ExoPlayer, HLS, UI, UI Leanback | воспроизведение, субтитры, связка с leanback | Apache-2.0 |
| [Glide](https://github.com/bumptech/glide) | загрузка и кэш постеров | BSD-2-Clause, частично MIT и Apache-2.0 |
| [jsoup](https://jsoup.org/) | разбор HTML-страниц сайта | MIT |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), Kotlin stdlib | фоновая загрузка | Apache-2.0 |
| [ZXing core](https://github.com/zxing/zxing) | QR-код на экране входа | Apache-2.0 |

Транзитивные зависимости (AndroidX core, fragment, recyclerview и т. д.) — Apache-2.0.

## Ресурсы

- Иконки `app/src/main/res/drawable/ic_*.xml` и `mark_*.xml` — контуры из [Material Icons](https://github.com/google/material-design-icons), Apache-2.0.

## Данные

- `app/src/main/assets/lists/oscars.json` — победители и номинанты «Оскара» из [Wikidata](https://www.wikidata.org/), CC0.
- `app/src/main/assets/lists/top.json` — из [наборов данных IMDb](https://developer.imdb.com/non-commercial-datasets/)
  (только для личного некоммерческого использования); в репозиторий не входит, собирается `tools/build_lists.py`.

## Только для сборки и тестов

| Компонент | Лицензия |
|---|---|
| Gradle Wrapper (`gradlew`, `gradle/wrapper/`) | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| [JSON-java (org.json)](https://github.com/stleary/JSON-java) — в юнит-тестах вместо заглушки Android | Public Domain |

## Ориентиры без заимствования кода

Плеер собран по схеме из [android/tv-samples](https://github.com/android/tv-samples) (Apache-2.0):
`VideoSupportFragment` + `PlaybackTransportControlGlue` + `LeanbackPlayerAdapter`.
[SmartTube](https://github.com/yuliskov/SmartTube) (MIT) использовался только как пример того, что ExoPlayer тянет 1080p
на этом телевизоре; его код не используется.
