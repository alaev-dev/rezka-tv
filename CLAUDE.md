# Rezka TV — заметки для Claude Code

Неофициальный клиент rezka для Android TV: родной ExoPlayer вместо плеера сайта, ТВ-интерфейс на leanback.
Пользовательская документация — README.md, сторонние компоненты — THIRD_PARTY.md.

## Устройство, под которое всё делается
- TCL на Android TV 9 (API 28), Realtek RTD284x (4× Cortex-A53), **32-битный ARM, 1,4 ГБ RAM** — слабый:
  всё меряется на нём.
- ADB по Wi-Fi: `adb connect <ip>:5555` (при первом подключении с нового компьютера подтвердить на ТВ).
- Release + AOT после каждой установки: `adb shell cmd package compile -m speed -f dev.alaev.rezkatv`.
  Debug-сборка на ТВ в 2–3 раза медленнее — замеры только на release.

## Планка качества (обязательна для любой задачи)
«Должно работать быстро и быть удобным, как rezka»: холодный старт < 1 с, отклик на кнопку < 100 мс, без рывков;
в отчёте — цифры (`adb shell dumpsys gfxinfo <пакет>`, время запуска) с release-сборки. Оформление: янтарный акцент,
белая скруглённая рамка фокуса с увеличением, только D-pad, «Назад» предсказуем, интерфейс на русском.

## Сборка и проверка
```sh
./gradlew testDebugUnitTest            # юнит-тесты (JUnit 4, app/src/test, фикстуры в app/src/test/resources)
./gradlew assembleRelease              # app/build/outputs/apk/release/app-release.apk
python3 -m unittest discover -s tools  # тесты tools/build_lists.py
```
- JDK 17, Android SDK platform 36. `local.properties` (sdk.dir) — свой на каждой машине, не в git.
- Ключ release — `keystore.properties` в корне (не в git); без него — отладочный ключ. Обновление на ТВ
  встаёт поверх только с тем же ключом, иначе удаление = потеря входа в rezka и прогресса.
- `app/src/main/assets/lists/top.json` (рейтинг IMDb) — не в git и не распространяется: `tools/build_lists.py`.

## Архитектура вкратце
- `rezka/` — клиент сайта (jsoup, кэш страниц на диске), `rezka/player/` — Media3 + leanback,
  `rezka/login/` — вход с телефона по QR (LAN-страница), `rezka/lists/` — «Рейтинг» и «Оскар».
- `session/` — «Продолжить после выключения», синхронизация прогресса с сайтом.
- `RezkaActivity` — режим «Сайт» (WebView) как запасной вариант.
- Логи: `adb logcat -s RezkaTv`.

## Правила
- Ничего тяжёлого: без больших фреймворков, без Compose; фон — корутины.
- Не коммитить: `top.json`, ключи, `keystore.properties`, `local.properties`.
- Коммиты — по-русски, «Область: что сделано».
