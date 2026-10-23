// Встраивается в каждую страницу rezka: заметный фокус для пульта и режим плеера на весь экран.
// Нативная часть (RezkaActivity) шлёт сюда команды через __tv.cmd(name, arg).
// window.__tvAutoplay = true (режим плеера): сразу разворачиваем плеер и запускаем видео.
(function () {
  if (window.__tv) return;
  var AUTOPLAY_TIMEOUT_MS = 30000;

  var style = document.createElement('style');
  style.textContent = [
    ':focus { outline: 4px solid #ffb300 !important; outline-offset: 2px !important; }',
    '::-webkit-scrollbar { display: none; }',
    'html.tv-player-open, html.tv-player-open body { overflow: hidden !important; }',
    'html.tv-autoplay body { background: #000 !important; }',
    '.tv-fs { position: fixed !important; left: 0 !important; top: 0 !important; width: 100vw !important;',
    '  height: 100vh !important; max-width: none !important; margin: 0 !important;',
    '  z-index: 2147483646 !important; background: #000 !important; }',
    '.tv-fs video { width: 100% !important; height: 100% !important; object-fit: contain !important; }',
    '#tv-osd { position: fixed; left: 50%; bottom: 8vh; transform: translateX(-50%); z-index: 2147483647;',
    '  padding: 12px 28px; border-radius: 12px; background: rgba(0,0,0,.75); color: #fff;',
    '  font: 600 32px/1.2 sans-serif; pointer-events: none; transition: opacity .2s; }',
  ].join('\n');
  (document.head || document.documentElement).appendChild(style);

  var fsBox = null;
  var osdTimer = 0;

  function bridge(on) {
    if (window.TVBridge) TVBridge.setPlayerMode(on);
  }

  function currentVideo() {
    if (fsBox) return fsBox.querySelector('video');
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) if (!videos[i].paused) return videos[i];
    return videos[0] || null;
  }

  function playerBox(video) {
    return video.closest('#oframecdnplayer, #cdnplayer, [id*="player"]') || video.parentElement;
  }

  function enter(video) {
    // Немые и маленькие видео (превью, реклама) не разворачиваем
    if (fsBox || video.muted || video.clientWidth < 300) return;
    fsBox = playerBox(video);
    fsBox.classList.add('tv-fs');
    document.documentElement.classList.add('tv-player-open');
    bridge(true);
  }

  function exit() {
    var video = currentVideo();
    if (video) video.pause();
    if (document.fullscreenElement) document.exitFullscreen();
    if (fsBox) fsBox.classList.remove('tv-fs');
    fsBox = null;
    document.documentElement.classList.remove('tv-player-open');
    bridge(false);
  }

  function fmt(sec) {
    if (!isFinite(sec)) return '--:--';
    sec = Math.floor(sec);
    var h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60, s = sec % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }

  function osd(text) {
    var el = document.getElementById('tv-osd');
    if (!el) {
      el = document.createElement('div');
      el.id = 'tv-osd';
    }
    // Внутри полноэкранного элемента, иначе его не видно
    var host = document.fullscreenElement || fsBox || document.body;
    if (el.parentNode !== host) host.appendChild(el);
    el.textContent = text;
    el.style.opacity = '1';
    clearTimeout(osdTimer);
    osdTimer = setTimeout(function () { el.style.opacity = '0'; }, 1500);
  }

  function position(video) {
    return fmt(video.currentTime) + ' / ' + fmt(video.duration);
  }

  function startPlayback(video) {
    // Плеер сайта (Playerjs) — через его API, иначе напрямую
    if (window.CDNPlayer && typeof CDNPlayer.api === 'function') CDNPlayer.api('play');
    else if (video) video.play();
  }

  // Плеер разворачивается сразу, видео запускаем, как только плеер готов
  function autoplay() {
    document.documentElement.classList.add('tv-autoplay');
    var started = Date.now();
    var timer = setInterval(function () {
      var video = document.querySelector('video');
      var box = video ? playerBox(video) : document.querySelector('#oframecdnplayer, #cdnplayer');
      if (box && !fsBox) {
        fsBox = box;
        box.classList.add('tv-fs');
        document.documentElement.classList.add('tv-player-open');
      }
      if (video && !video.paused) {
        bridge(true);
        clearInterval(timer);
      } else if (box) {
        startPlayback(video);
      }
      if (Date.now() - started > AUTOPLAY_TIMEOUT_MS) clearInterval(timer);
    }, 500);
  }

  // play не всплывает, ловим на фазе перехвата
  document.addEventListener('play', function (e) {
    if (e.target.tagName === 'VIDEO') enter(e.target);
  }, true);

  document.addEventListener('fullscreenchange', function () {
    bridge(!!document.fullscreenElement || !!fsBox);
  }, true);

  window.__tv = {
    cmd: function (name, arg) {
      if (name === 'exit') return exit();
      var video = currentVideo();
      if (!video) return;
      switch (name) {
        case 'toggle':
          if (video.paused) video.play(); else video.pause();
          osd(video.paused ? '❚❚  ' + position(video) : '▶');
          break;
        case 'play':
          video.play();
          break;
        case 'pause':
          video.pause();
          break;
        case 'seek':
          var end = isFinite(video.duration) ? video.duration - 1 : Infinity;
          video.currentTime = Math.max(0, Math.min(end, video.currentTime + arg));
          osd((arg > 0 ? '» ' : '« ') + position(video));
          break;
        case 'controls':
          // Показать собственную панель плеера сайта
          video.dispatchEvent(new MouseEvent('mousemove', { bubbles: true }));
          osd(position(video));
          break;
      }
    },
  };

  if (window.__tvAutoplay) autoplay();
})();
