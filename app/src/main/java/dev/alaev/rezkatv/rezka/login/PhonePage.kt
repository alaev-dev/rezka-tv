package dev.alaev.rezkatv.rezka.login

object PhonePage {

    val HTML = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>Вход в rezka на телевизоре</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
         background: #0B0E13; color: #fff; font: 16px/1.4 -apple-system, "Segoe UI", Roboto, sans-serif; padding: 20px; }
  main { width: 100%; max-width: 380px; background: #161B23; border-radius: 16px; padding: 28px 22px; }
  h1 { margin: 0 0 6px; font-size: 22px; }
  p.hint { margin: 0 0 22px; color: #9AA4B2; font-size: 14px; }
  label { display: block; margin: 14px 0 6px; color: #9AA4B2; font-size: 14px; }
  .field { position: relative; }
  input { width: 100%; padding: 13px 14px; border-radius: 10px; border: 1px solid #2C3440; background: #0B0E13;
          color: #fff; font-size: 17px; outline: none; }
  input:focus { border-color: #FFB300; }
  #pass { padding-right: 96px; }
  #toggle { position: absolute; right: 6px; top: 6px; bottom: 6px; padding: 0 12px; border: 0; border-radius: 8px;
            background: #232A35; color: #fff; font-size: 14px; }
  #submit { width: 100%; margin-top: 24px; padding: 14px; border: 0; border-radius: 10px; background: #FFB300;
            color: #0B0E13; font-size: 17px; font-weight: 700; }
  #submit:disabled { opacity: .55; }
  #status { min-height: 22px; margin-top: 16px; text-align: center; font-size: 15px; }
  .error { color: #FF7B6B; } .ok { color: #7EE08A; font-weight: 600; }
  #back { display: none; width: 100%; margin-top: 16px; padding: 14px; border: 0; border-radius: 10px;
          background: #232A35; color: #fff; font-size: 17px; }
  p.tip { margin: 18px 0 0; color: #6B7584; font-size: 13px; }
</style>
</head>
<body>
<main>
  <h1>Вход в rezka</h1>
  <p class="hint">Телевизор войдёт в ваш аккаунт rezka. Пароль уходит только на сайт и нигде не сохраняется.</p>
  <form id="form" autocomplete="on">
    <label for="email">Email</label>
    <input id="email" name="email" type="email" autocomplete="username" inputmode="email" required>
    <label for="pass">Пароль</label>
    <div class="field">
      <input id="pass" name="password" type="password" autocomplete="current-password" required>
      <button id="toggle" type="button">Показать</button>
    </div>
    <button id="submit" type="submit">Войти</button>
  </form>
  <div id="status" role="status"></div>
  <button id="back" type="button" onclick="history.back()">Ввести ещё раз</button>
  <p class="tip">Пароль не подставляется? Отсканируйте QR-код на телевизоре: та страница открывается по HTTPS,
    и менеджер паролей предложит пароль от rezka.</p>
</main>
<script>
  var form = document.getElementById('form'), pass = document.getElementById('pass'),
      toggle = document.getElementById('toggle'), submit = document.getElementById('submit'),
      status = document.getElementById('status'), back = document.getElementById('back');
  var key = new URLSearchParams(location.search).get('k') || '';
  function show(text, cls) { status.textContent = text; status.className = cls || ''; }
  toggle.onclick = function () {
    var hidden = pass.type === 'password';
    pass.type = hidden ? 'text' : 'password';
    toggle.textContent = hidden ? 'Скрыть' : 'Показать';
  };
  function send(fields, onRetry) {
    submit.disabled = true;
    show('Проверяем…');
    fields.k = key;
    fetch('/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(fields).toString() })
      .then(function (r) { return r.json(); })
      .then(function (j) {
        if (j.ok) { show('Готово! Можно вернуться к телевизору', 'ok'); form.style.display = 'none'; return; }
        show(j.message || 'Не удалось войти', 'error');
        submit.disabled = !!j.final;
        if (!j.final) onRetry();
      })
      .catch(function () { show('Нет связи с телевизором. Проверьте, что телефон в той же Wi-Fi сети', 'error'); submit.disabled = false; onRetry(); });
  }
  form.onsubmit = function (e) {
    e.preventDefault();
    send({ email: document.getElementById('email').value.trim(), password: pass.value }, function () {});
  };
  var sealed = new URLSearchParams(location.hash.slice(1));
  if (sealed.get('c')) {
    history.replaceState(null, '', location.pathname + location.search);
    form.style.display = 'none';
    send({ p: sealed.get('p') || '', iv: sealed.get('iv') || '', c: sealed.get('c') }, function () {
      back.style.display = 'block';
    });
  }
</script>
</body>
</html>
""".trimIndent()
}
