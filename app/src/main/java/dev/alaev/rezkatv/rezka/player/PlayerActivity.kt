package dev.alaev.rezkatv.rezka.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.widget.ThumbsBar
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.ChoicePanel
import dev.alaev.rezkatv.rezka.Details
import dev.alaev.rezkatv.rezka.EXTRA_POSTER
import dev.alaev.rezkatv.rezka.EXTRA_TITLE
import dev.alaev.rezkatv.rezka.EXTRA_URL
import dev.alaev.rezkatv.rezka.Episode
import dev.alaev.rezkatv.rezka.EpisodeNav
import dev.alaev.rezkatv.rezka.EpisodesPanel
import dev.alaev.rezkatv.rezka.Loader
import dev.alaev.rezkatv.rezka.PlayLink
import dev.alaev.rezkatv.rezka.Progress
import dev.alaev.rezkatv.rezka.Quality
import dev.alaev.rezkatv.rezka.QualityPicker
import dev.alaev.rezkatv.rezka.ResumeStore
import dev.alaev.rezkatv.rezka.RezkaClient
import dev.alaev.rezkatv.rezka.Settings
import dev.alaev.rezkatv.rezka.SettingsActivity
import dev.alaev.rezkatv.rezka.Streams
import dev.alaev.rezkatv.rezka.Subtitle
import dev.alaev.rezkatv.rezka.Tile
import dev.alaev.rezkatv.rezka.Translator
import dev.alaev.rezkatv.rezka.WatchedStore
import dev.alaev.rezkatv.rezka.playOnSite
import dev.alaev.rezkatv.session.ResumePlanner
import dev.alaev.rezkatv.session.SessionStore
import dev.alaev.rezkatv.session.SitePosition
import dev.alaev.rezkatv.session.WatchSession

@UnstableApi
class PlayerActivity : FragmentActivity() {

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var client: RezkaClient
    private lateinit var settings: Settings
    private lateinit var resume: ResumeStore
    private lateinit var loader: Loader
    private lateinit var player: ExoPlayer
    private lateinit var fragment: VideoSupportFragment
    private var glue: PlayerGlue? = null

    private lateinit var episodesPanel: EpisodesPanel
    private lateinit var choicePanel: ChoicePanel
    private lateinit var subtitlesView: SubtitleView
    private lateinit var loadingView: View
    private lateinit var loadingTitle: TextView
    private lateinit var loadingInfo: TextView
    private lateinit var osd: TextView
    private lateinit var nextUp: View
    private lateinit var nextUpTitle: TextView
    private lateinit var nextUpCountdown: TextView
    private lateinit var nextUpProgress: ProgressBar
    private lateinit var errorView: TextView

    private var details: Details? = null
    private var translator: Translator? = null
    private var episodes: List<Episode> = emptyList()
    private var episode: Episode? = null
    private var streams: Streams? = null
    private var quality: Quality? = null
    private var subtitle: Subtitle? = null
    private var urlIndex = 0
    private var tiles: List<Tile> = emptyList()
    private var seekProvider: ThumbnailSeekProvider? = null
    private var seekProviderKey = ""
    private var startJob: Job? = null
    private var preparing = false
    private var refreshedAfterError = false
    private var stopped = false
    private var requestedPosition = RESUME
    private var autoplay = true

    private lateinit var sessions: SessionStore
    private var reported: SitePosition? = null
    private var deliberateExit = false
    private var screenOffAt = 0L

    private var serverSaved = false
    private var lastServerSave = 0L
    private var lastLocalSave = 0L
    private var nextUpDismissed = false
    private var nextUpLeftMs = -1L

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }
    private val commitSeek = Runnable { commitSeek() }
    private val hideOsd = Runnable { osd.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        client = RezkaClient.get(this)
        settings = Settings(this)
        resume = ResumeStore(this)
        sessions = SessionStore(this)
        loader = Loader(this, null)

        episodesPanel = EpisodesPanel(findViewById(R.id.episodes_panel))
        choicePanel = ChoicePanel(findViewById(R.id.choice_panel))
        subtitlesView = findViewById(R.id.subtitles)
        loadingView = findViewById(R.id.loading)
        loadingTitle = findViewById(R.id.loading_title)
        loadingInfo = findViewById(R.id.loading_info)
        osd = findViewById(R.id.osd)
        nextUp = findViewById(R.id.next_up)
        nextUpTitle = findViewById(R.id.next_up_title)
        nextUpCountdown = findViewById(R.id.next_up_countdown)
        nextUpProgress = findViewById(R.id.next_up_progress)
        errorView = findViewById(R.id.error)
        loadingTitle.text = intent.getStringExtra(EXTRA_TITLE)
        subtitlesView.setStyle(
            CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null),
        )
        subtitlesView.setBottomPaddingFraction(SUBTITLES_BOTTOM)

        player = buildPlayer()
        fragment = VideoSupportFragment()
        supportFragmentManager.beginTransaction().replace(R.id.playback, fragment).commitNow()
        fragment.backgroundType = PlaybackSupportFragment.BG_LIGHT
        fragment.setFadeCompleteListener(object : PlaybackSupportFragment.OnFadeCompleteListener() {
            override fun onFadeInComplete() = subtitlesView.setBottomPaddingFraction(SUBTITLES_ABOVE_CONTROLS)
            override fun onFadeOutComplete() = subtitlesView.setBottomPaddingFraction(SUBTITLES_BOTTOM)
        })

        load()
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        handler.post(tick)
        val sleptMs = if (screenOffAt > 0) SystemClock.elapsedRealtime() - screenOffAt else 0
        screenOffAt = 0
        if (sleptMs >= WAKE_SYNC_AFTER_MS && details != null) resyncAfterWake()
    }

    override fun onResume() {
        super.onResume()
        installSeekProvider()
    }

    override fun onStop() {
        stopped = true
        handler.removeCallbacks(tick)
        val screenOff = !getSystemService(PowerManager::class.java).isInteractive
        if (screenOff) screenOffAt = SystemClock.elapsedRealtime()
        saveProgress(toServer = true, screenOff = screenOff)
        player.pause()
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        deliberateExit = true
        saveProgress(toServer = false)
        super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        player.release()
        super.onDestroy()
    }


    private fun load() {
        val url = requireNotNull(intent.getStringExtra(EXTRA_URL))
        scope.launch {
            val sync = intent.getStringExtra(EXTRA_TRANSLATOR) == null
            val d = loader.load { if (sync) client.details(url) else client.recentDetails(url) } ?: return@launch showError()
            details = d
            val local = sessions.read()?.takeIf { it.app == WatchSession.APP_REZKA && it.url == d.url }
            reported = local?.reported
            val plan = if (sync) ResumePlanner.plan(local, d.sitePosition()) else null
            val wanted = plan?.translatorId ?: intent.getStringExtra(EXTRA_TRANSLATOR)
            val t = d.translators.find { it.id == wanted }
                ?: d.translators.find { it.id == d.position?.translatorId }
                ?: d.translators.firstOrNull()
                ?: return@launch fallbackToSite()
            translator = t
            episodes = if (d.isSeries && t.id != d.position?.translatorId) {
                loader.load { client.translatorEpisodes(d, t) }?.takeIf { it.isNotEmpty() } ?: d.episodes
            } else {
                d.episodes
            }
            var target = if (d.isSeries) {
                EpisodeNav.find(episodes, plan?.season ?: intent.getIntExtra(EXTRA_SEASON, 0), plan?.episode ?: intent.getIntExtra(EXTRA_EPISODE, 0))
                    ?: d.currentEpisode?.let { EpisodeNav.find(episodes, it.season, it.episode) }
                    ?: episodes.firstOrNull()
            } else {
                null
            }
            var position = when {
                intent.getBooleanExtra(EXTRA_FROM_START, false) -> 0
                plan != null && plan.positionMs >= 0 && local != null -> Progress.startPosition(plan.positionMs, local.durationMs)
                else -> RESUME
            }
            if (plan != null && !plan.fromOtherDevice && local != null && Progress.isFinished(local.positionMs, local.durationMs)) {
                target?.let { EpisodeNav.next(episodes, it.season, it.episode) }?.let {
                    target = it
                    position = 0
                }
            }
            createGlue(d.isSeries)
            start(target, position)
            if (plan?.fromOtherDevice == true) showOtherDeviceOsd(target, t)
        }
    }

    private fun Details.sitePosition() = position?.let { SitePosition(it.translatorId, it.season, it.episode) }

    private fun showOtherDeviceOsd(target: Episode?, t: Translator) {
        showOsd(
            if (target != null) getString(R.string.resumed_other_device_episode, target.code)
            else getString(R.string.resumed_other_device_translator, t.name),
            OSD_LONG_MS,
        )
    }

    private fun resyncAfterWake() {
        val url = details?.url ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        scope.launch {
            val d = loader.quietly { client.details(url) } ?: return@launch
            details = d
            val local = currentSession(interrupted = true)
            val plan = ResumePlanner.plan(local, d.sitePosition()) ?: return@launch
            sessions.update { it.copy(promptedAt = System.currentTimeMillis()) }
            val chosen = d.translators.find { it.id == plan.translatorId } ?: translator ?: return@launch
            if (!plan.fromOtherDevice) {
                autoplay = false
                return@launch start(episode, position)
            }
            if (chosen != translator && d.isSeries) {
                episodes = loader.quietly { client.translatorEpisodes(d, chosen) }?.takeIf { it.isNotEmpty() } ?: episodes
            }
            translator = chosen
            val target = if (d.isSeries) EpisodeNav.find(episodes, plan.season, plan.episode) ?: episode else null
            autoplay = false
            start(target, if (plan.positionMs >= 0) plan.positionMs else RESUME)
            showOtherDeviceOsd(target, chosen)
        }
    }

    private fun createGlue(series: Boolean) {
        val adapter = LeanbackPlayerAdapter(this, player, PROGRESS_UPDATE_MS)
        val g = PlayerGlue(this, adapter, series, ::onAction)
        g.host = VideoSupportFragmentGlueHost(fragment)
        fragment.setOnKeyInterceptListener { v, keyCode, event -> onPlaybackKey(keyCode, event) || g.onKey(v, keyCode, event) }
        glue = g
    }

    private fun start(target: Episode?, positionMs: Long) {
        val d = details ?: return
        val t = translator ?: return
        preparing = true
        requestedPosition = positionMs
        episode = target
        serverSaved = false
        nextUpDismissed = false
        hideNextUp()
        errorView.visibility = View.GONE
        updateTitles()
        showLoading()

        startJob?.cancel()
        startJob = scope.launch {
            val s = loader.load { client.streams(d, t, target) } ?: return@launch showError()
            if (s.qualities.isEmpty()) return@launch fallbackToSite()
            streams = s
            quality = QualityPicker.pick(s.qualities, settings.quality, screenHeight())
            subtitle = s.subtitles.find { it.language != null && it.language == s.defaultSubtitle }
            glue?.setSubtitlesAvailable(s.subtitles.isNotEmpty())
            val position = if (positionMs != RESUME) positionMs else {
                resume.get(d.id, target?.season ?: 0, target?.episode ?: 0)
                    ?.let { Progress.startPosition(it.positionMs, it.durationMs) } ?: 0
            }
            prepare(position, 0)
            loadThumbnails(s.thumbnails)
        }
    }

    private fun prepare(positionMs: Long, index: Int) {
        val q = quality ?: return
        val s = streams ?: return
        urlIndex = index
        val url = q.urls[index]
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (url.endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(s.subtitles.map {
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(it.language)
                    .setLabel(it.label)
                    .setId(it.label)
                    .build()
            })
            .build()
        Log.i(TAG, "play ${episode?.code ?: "film"} ${q.label} #$index")
        player.setMediaItem(item, positionMs)
        player.prepare()
        player.playWhenReady = !stopped && autoplay
        autoplay = true
        applySubtitles()
    }

    private fun loadThumbnails(path: String?) {
        tiles = emptyList()
        seekProviderKey = ""
        installSeekProvider()
        path ?: return
        val forEpisode = episode
        scope.launch {
            val loaded = loader.quietly { client.thumbnails(path) } ?: return@launch
            if (episode != forEpisode) return@launch
            tiles = loaded
            seekProviderKey = ""
            installSeekProvider()
        }
    }

    private fun installSeekProvider() {
        val g = glue ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val step = settings.seekStepMs
        val key = "$duration/$step/${tiles.size}"
        if (key == seekProviderKey) return
        seekProviderKey = key
        seekProvider = ThumbnailSeekProvider(this, scope, duration, step, tiles, ::onSeekStateChanged, ::moveThumbnail)
        g.seekProvider = seekProvider
    }

    private fun buildPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(RezkaClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_TIMEOUT_MS)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 2_000, 4_000)
            .setTargetBufferBytes(BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .build()
            .also { it.addListener(PlayerListener()) }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    preparing = false
                    refreshedAfterError = false
                    loadingView.visibility = View.GONE
                    installSeekProvider()
                }
                Player.STATE_ENDED -> onEnded()
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && !player.playWhenReady) handler.post {
                if (!player.playWhenReady && seekProvider?.seeking != true) saveProgress(toServer = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "playback error ${error.errorCodeName}", error)
            val q = quality
            val position = player.currentPosition.coerceAtLeast(0)
            when {
                q != null && urlIndex + 1 < q.urls.size -> prepare(position, urlIndex + 1)
                !refreshedAfterError -> {
                    refreshedAfterError = true
                    start(episode, position)
                }
                else -> showError()
            }
        }

        override fun onCues(cueGroup: CueGroup) = subtitlesView.setCues(cueGroup.cues)

        override fun onTracksChanged(tracks: Tracks) {
            val wanted = subtitle ?: return
            if (wanted.language != null) return
            val selected = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected && it.mediaTrackGroup.getFormat(0).label == wanted.label }
            if (!selected) applySubtitles()
        }
    }


    private fun onAction(id: Long) {
        when (id) {
            PlayerGlue.ACTION_PREV -> switchEpisode(forward = false)
            PlayerGlue.ACTION_NEXT -> switchEpisode(forward = true)
            PlayerGlue.ACTION_EPISODES -> showEpisodes()
            PlayerGlue.ACTION_TRANSLATOR -> showTranslators()
            PlayerGlue.ACTION_QUALITY -> showQualities()
            PlayerGlue.ACTION_SUBTITLES -> showSubtitles()
            PlayerGlue.ACTION_SETTINGS -> {
                glue?.pause()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun onPlaybackKey(keyCode: Int, event: KeyEvent): Boolean {
        val hidden = !fragment.isControlsOverlayVisible
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (hidden) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) togglePlay()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val bar = progressBar() ?: return false
                if (hidden || bar.hasFocus()) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (!bar.hasFocus()) bar.requestFocus()
                        repeat(1 + SeekMath.extraSteps(event.repeatCount)) { bar.dispatchKeyEvent(event) }
                        handler.removeCallbacks(commitSeek)
                        handler.postDelayed(commitSeek, SEEK_COMMIT_MS)
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val key = event.keyCode
        val back = key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE
        val ok = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
        when {
            choicePanel.isShowing && back -> { if (!down) choicePanel.hide(); return true }
            episodesPanel.isShowing && back -> { if (!down) episodesPanel.hide(); return true }
            choicePanel.isShowing || episodesPanel.isShowing -> return super.dispatchKeyEvent(event)
            errorView.visibility == View.VISIBLE && ok -> { if (!down) retry(); return true }
            nextUp.visibility == View.VISIBLE && back -> { if (!down) dismissNextUp(); return true }
            nextUp.visibility == View.VISIBLE && ok -> { if (!down) switchEpisode(forward = true); return true }
            glue == null && !back -> return true
        }
        if (down && event.repeatCount == 0) {
            when (key) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> { switchEpisode(forward = true); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { switchEpisode(forward = false); return true }
            }
        }
        if (key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (down) seekBy(if (key == KeyEvent.KEYCODE_MEDIA_REWIND) -settings.seekStepMs else settings.seekStepMs)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun togglePlay() {
        val g = glue ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) g.pause() else g.play()
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
        fragment.tickle()
    }

    private fun commitSeek() {
        if (seekProvider?.seeking != true) return
        val bar = progressBar() ?: return
        val time = SystemClock.uptimeMillis()
        bar.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        bar.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    }

    private fun onSeekStateChanged(seeking: Boolean) {
        if (!seeking) {
            handler.removeCallbacks(commitSeek)
            return
        }
        val thumbs = fragment.view?.findViewById<ThumbsBar>(androidx.leanback.R.id.thumbs_row) ?: return
        if (thumbs.childCount != 1) {
            val w = resources.getDimensionPixelSize(R.dimen.player_thumb_width)
            val h = resources.getDimensionPixelSize(R.dimen.player_thumb_height)
            thumbs.setThumbSize(w, h)
            thumbs.setHeroThumbSize(w, h)
            thumbs.setNumberOfThumbs(1)
        }
    }

    private fun moveThumbnail(positionMs: Long) {
        val root = fragment.view ?: return
        val bar = root.findViewById<View>(androidx.leanback.R.id.playback_progress) ?: return
        val thumbs = root.findViewById<View>(androidx.leanback.R.id.thumbs_row) ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val half = resources.getDimensionPixelSize(R.dimen.player_thumb_width) / 2f
        val knobX = bar.left + bar.paddingLeft + (bar.width - bar.paddingLeft - bar.paddingRight) * positionMs.toFloat() / duration
        val barCenter = thumbs.width / 2f
        val minX = bar.left + half
        val maxX = bar.right - half
        thumbs.translationX = knobX.coerceIn(minX, maxX) - barCenter
    }

    private fun progressBar(): View? = fragment.view?.findViewById(androidx.leanback.R.id.playback_progress)


    private fun switchEpisode(forward: Boolean) {
        val current = episode ?: return
        val target = if (forward) EpisodeNav.next(episodes, current.season, current.episode)
        else EpisodeNav.previous(episodes, current.season, current.episode)
        if (target == null) {
            Toast.makeText(this, if (forward) R.string.last_episode else R.string.first_episode, Toast.LENGTH_SHORT).show()
            return
        }
        playEpisode(target, fromStart = forward)
    }

    private fun playEpisode(target: Episode, fromStart: Boolean) {
        saveProgress(toServer = true)
        start(target, if (fromStart) 0 else RESUME)
        showOsd("${target.code} · ${target.name}")
    }

    private fun showEpisodes() {
        val d = details ?: return
        val current = episode
        episodesPanel.show(d.title, episodes, current, label = { e ->
            when {
                e == current -> "▶  ${e.name}  ·  ${getString(R.string.now_playing)}"
                resume.get(d.id, e.season, e.episode)?.finished == true -> "${e.name}  ✓"
                else -> e.name
            }
        }) { if (it != current) playEpisode(it, fromStart = false) }
    }

    private fun showTranslators() {
        val d = details ?: return
        val current = translator
        choicePanel.show(getString(R.string.translators), d.translators.map { it.name }, d.translators.indexOf(current)) { i ->
            val chosen = d.translators[i]
            if (chosen == current) return@show
            switchTranslator(d, chosen)
        }
    }

    private fun switchTranslator(d: Details, chosen: Translator) {
        val position = player.currentPosition.coerceAtLeast(0)
        val current = episode
        saveProgress(toServer = false)
        startJob?.cancel()
        startJob = scope.launch {
            if (!d.isSeries) {
                translator = chosen
                return@launch start(null, position)
            }
            val list = loader.load { client.translatorEpisodes(d, chosen) }?.takeIf { it.isNotEmpty() } ?: return@launch
            translator = chosen
            episodes = list
            val same = current?.let { EpisodeNav.find(list, it.season, it.episode) }
            if (same != null) start(same, position) else start(list.first(), 0)
        }
    }

    private fun showQualities() {
        val s = streams ?: return
        val auto = QualityPicker.pick(s.qualities, Settings.AUTO, screenHeight())
        val labels = listOf(getString(R.string.quality_auto_current, auto?.label.orEmpty())) + s.qualities.map { it.label }
        val selected = if (settings.quality == Settings.AUTO) 0 else s.qualities.indexOf(quality) + 1
        choicePanel.show(getString(R.string.action_quality), labels, selected) { i ->
            settings.setQuality(if (i == 0) Settings.AUTO else s.qualities[i - 1].label)
            val chosen = if (i == 0) auto else s.qualities[i - 1]
            if (chosen != null && chosen != quality) {
                quality = chosen
                prepare(player.currentPosition.coerceAtLeast(0), 0)
            }
        }
    }

    private fun showSubtitles() {
        val s = streams ?: return
        val labels = listOf(getString(R.string.subtitles_off)) + s.subtitles.map { it.label }
        choicePanel.show(getString(R.string.action_subtitles), labels, s.subtitles.indexOf(subtitle) + 1) { i ->
            subtitle = if (i == 0) null else s.subtitles[i - 1]
            applySubtitles()
        }
    }

    private fun applySubtitles() {
        val wanted = subtitle
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, wanted == null)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (wanted != null) {
            params.setPreferredTextLanguage(wanted.language)
            if (wanted.language == null) {
                player.currentTracks.groups
                    .find { it.type == C.TRACK_TYPE_TEXT && it.mediaTrackGroup.getFormat(0).label == wanted.label }
                    ?.let { params.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
            }
        }
        player.trackSelectionParameters = params.build()
        if (wanted == null) subtitlesView.setCues(emptyList())
    }


    private fun onTick() {
        if (player.isPlaying) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastLocalSave >= LOCAL_SAVE_MS) saveProgress(toServer = false)
            val firstSave = !serverSaved && player.currentPosition >= FIRST_SAVE_AFTER_MS
            if (firstSave || (serverSaved && now - lastServerSave >= SERVER_SAVE_MS)) saveProgress(toServer = true)
        }
        updateNextUp()
    }

    private fun updateNextUp() {
        val current = episode ?: return
        val next = EpisodeNav.next(episodes, current.season, current.episode) ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val remaining = duration - player.currentPosition
        if (nextUp.visibility != View.VISIBLE) {
            if (!nextUpDismissed && remaining in 1..NEXT_UP_BEFORE_END_MS && player.isPlaying) showNextUp(next, countdown = settings.autoplayNext)
            return
        }
        if (remaining > NEXT_UP_BEFORE_END_MS + NEXT_UP_HYSTERESIS_MS) return hideNextUp()
        if (nextUpLeftMs < 0 || !player.isPlaying) return
        nextUpLeftMs -= TICK_MS
        if (nextUpLeftMs <= 0) return switchEpisode(forward = true)
        renderCountdown(nextUpLeftMs)
    }

    private fun showNextUp(next: Episode, countdown: Boolean) {
        nextUpTitle.text = "${next.code} · ${next.name}"
        nextUpLeftMs = if (countdown) NEXT_UP_COUNTDOWN_MS else -1L
        nextUpProgress.visibility = if (countdown) View.VISIBLE else View.GONE
        nextUpCountdown.visibility = if (countdown) View.VISIBLE else View.GONE
        if (countdown) renderCountdown(NEXT_UP_COUNTDOWN_MS)
        fragment.hideControlsOverlay(true)
        nextUp.visibility = View.VISIBLE
    }

    private fun renderCountdown(leftMs: Long) {
        nextUpCountdown.text = getString(R.string.next_up_in, ((leftMs + 999) / 1000).toInt())
        nextUpProgress.progress = (1000 - leftMs * 1000 / NEXT_UP_COUNTDOWN_MS).toInt()
    }

    private fun hideNextUp() {
        nextUp.visibility = View.GONE
        nextUpLeftMs = -1L
    }

    private fun dismissNextUp() {
        nextUpDismissed = true
        hideNextUp()
    }

    private fun onEnded() {
        saveProgress(toServer = true)
        val current = episode
        val next = current?.let { EpisodeNav.next(episodes, it.season, it.episode) }
        when {
            next != null && settings.autoplayNext && !nextUpDismissed -> switchEpisode(forward = true)
            next != null && !nextUpDismissed -> showNextUp(next, countdown = false)
            else -> fragment.showControlsOverlay(true)
        }
    }


    private fun saveProgress(toServer: Boolean, screenOff: Boolean = false) {
        if (preparing) return
        val d = details ?: return
        val t = translator ?: return
        val session = currentSession(interrupted = !deliberateExit && player.playbackState != Player.STATE_ENDED, screenOff) ?: return
        val ep = episode
        resume.put(d.id, session.season, session.episode, session.positionMs, session.durationMs)
        if (ep == null && WatchedStore.reached(session.positionMs, session.durationMs)) WatchedStore.get(this).set(d.id, true)
        sessions.write(session)
        lastLocalSave = SystemClock.elapsedRealtime()
        if (!toServer) return
        serverSaved = true
        lastServerSave = lastLocalSave
        val sent = SitePosition(t.id, session.season, session.episode)
        background.launch {
            runCatching { client.saveProgress(d, t.id, ep, session.positionMs, session.durationMs) }
                .onSuccess {
                    handler.post { reported = sent }
                    sessions.update { if (it.url == d.url) it.copy(reported = sent) else it }
                }
                .onFailure { Log.w(TAG, "save progress failed: $it") }
        }
    }

    private fun currentSession(interrupted: Boolean, screenOff: Boolean = false): WatchSession? {
        val d = details ?: return null
        val t = translator ?: return null
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0 || player.playbackState == Player.STATE_IDLE) return null
        val position = if (player.playbackState == Player.STATE_ENDED) duration else player.currentPosition
        return WatchSession(
            app = WatchSession.APP_REZKA,
            url = d.url,
            title = d.title,
            poster = d.poster,
            translatorId = t.id,
            translatorName = t.name,
            season = episode?.season ?: 0,
            episode = episode?.episode ?: 0,
            positionMs = position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
            bootCount = SessionStore.bootCount(this),
            interrupted = interrupted,
            stoppedByScreenOff = screenOff,
            reported = reported,
        )
    }


    private fun updateTitles() {
        val d = details ?: return
        val ep = episode
        val subtitle = listOfNotNull(ep?.let { "${it.code} · ${it.name}" }, translator?.name).joinToString("  ·  ")
        glue?.title = d.title
        glue?.subtitle = subtitle
        loadingTitle.text = d.title
        loadingInfo.text = subtitle
    }

    private fun showLoading() {
        if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) loadingView.visibility = View.VISIBLE
        else player.pause()
    }

    private fun showOsd(text: String, durationMs: Long = OSD_MS) {
        osd.text = text
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, durationMs)
    }

    private fun showError() {
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorView.requestFocus()
    }

    private fun retry() {
        errorView.visibility = View.GONE
        when {
            details == null -> load()
            preparing -> start(episode, requestedPosition)
            else -> start(episode, player.currentPosition.coerceAtLeast(0))
        }
    }

    private fun fallbackToSite() {
        val d = details
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        Toast.makeText(this, R.string.no_streams, Toast.LENGTH_LONG).show()
        val t = translator
        playOnSite(if (d != null && t != null) PlayLink.build(d.url, t.id, episode?.season ?: 0, episode?.episode ?: 0) else url)
        finish()
    }

    private fun screenHeight() = Util.getCurrentDisplayModeSize(this).y

    companion object {
        private const val TAG = "RezkaTv"
        private const val EXTRA_TRANSLATOR = "translator"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val EXTRA_FROM_START = "from_start"
        private const val RESUME = -1L
        private const val PROGRESS_UPDATE_MS = 500
        private const val TICK_MS = 1000L
        private const val SEEK_COMMIT_MS = 1500L
        private const val OSD_MS = 3000L
        private const val OSD_LONG_MS = 6000L
        private const val LOCAL_SAVE_MS = 10_000L
        private const val SERVER_SAVE_MS = 60_000L
        private const val WAKE_SYNC_AFTER_MS = 60_000L
        private const val FIRST_SAVE_AFTER_MS = 5_000L
        private const val NEXT_UP_BEFORE_END_MS = 20_000L
        private const val NEXT_UP_HYSTERESIS_MS = 5_000L
        private const val NEXT_UP_COUNTDOWN_MS = 10_000L
        private const val HTTP_TIMEOUT_MS = 15_000
        private const val BUFFER_BYTES = 48 * 1024 * 1024
        private const val SUBTITLES_ABOVE_CONTROLS = 0.34f
        private const val SUBTITLES_BOTTOM = 0.1f

        private val background = CoroutineScope(SupervisorJob() + RezkaClient.io)

        fun intent(
            context: Context,
            url: String,
            title: String?,
            poster: String?,
            translatorId: String? = null,
            season: Int = 0,
            episode: Int = 0,
            fromStart: Boolean = false,
        ): Intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_POSTER, poster)
            .putExtra(EXTRA_TRANSLATOR, translatorId)
            .putExtra(EXTRA_SEASON, season)
            .putExtra(EXTRA_EPISODE, episode)
            .putExtra(EXTRA_FROM_START, fromStart)
    }
}
