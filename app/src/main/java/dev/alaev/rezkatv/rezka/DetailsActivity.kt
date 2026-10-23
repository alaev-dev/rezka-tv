package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.player.PlayerActivity

/** Страница тайтла: обложка, описание, «Продолжить», следующая серия, сезоны и серии, озвучка. */
class DetailsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var client: RezkaClient
    private lateinit var resume: ResumeStore
    private lateinit var loader: Loader
    private lateinit var url: String
    private lateinit var episodesPanel: EpisodesPanel
    private lateinit var choicePanel: ChoicePanel

    private lateinit var description: TextView
    private lateinit var playButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var episodesButton: TextView
    private lateinit var translatorButton: TextView
    private lateinit var watchedButton: TextView
    private lateinit var watched: WatchedStore

    private var details: Details? = null
    private var translator: Translator? = null
    /** Серии выбранной озвучки (у разных озвучек списки бывают разными) */
    private var episodes: List<Episode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        url = requireNotNull(intent.getStringExtra(EXTRA_URL))
        client = RezkaClient.get(this)
        resume = ResumeStore(this)
        loader = Loader(this, null)
        episodesPanel = EpisodesPanel(findViewById(R.id.episodes_panel))
        choicePanel = ChoicePanel(findViewById(R.id.choice_panel))

        description = findViewById(R.id.description)
        playButton = findViewById(R.id.play)
        nextButton = findViewById(R.id.next)
        episodesButton = findViewById(R.id.episodes)
        translatorButton = findViewById(R.id.translator)
        watchedButton = findViewById(R.id.watched)
        watched = WatchedStore.get(this)

        // Пока грузится — то, что уже известно по карточке
        findViewById<TextView>(R.id.title).text = intent.getStringExtra(EXTRA_TITLE)
        Images.art(findViewById(R.id.art), "", intent.getStringExtra(EXTRA_POSTER))
        description.setText(R.string.loading)

        playButton.setOnClickListener { playPrimary() }
        nextButton.setOnClickListener { primary().next?.let { play(it, fromStart = true) } }
        episodesButton.setOnClickListener { showEpisodes() }
        translatorButton.setOnClickListener { showTranslators() }
        watchedButton.setOnClickListener { details?.let { watched.toggle(it.id) }; updateButtons() }
        findViewById<View>(R.id.site).setOnClickListener { openSite(url) }

        client.cachedDetails(url)?.let { bind(it.value) }
        load()
    }

    override fun onRestart() {
        super.onRestart()
        // После просмотра сайт знает новую позицию, а у нас — новое место остановки
        load()
        updateButtons()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            choicePanel.isShowing -> choicePanel.hide()
            episodesPanel.isShowing -> episodesPanel.hide()
            else -> super.onBackPressed()
        }
    }

    private fun load() {
        scope.launch {
            val fresh = loader.load { client.details(url) }
            if (fresh != null) bind(fresh) else if (details == null) description.setText(R.string.load_failed)
        }
    }

    private fun bind(d: Details) {
        val sameTitle = details?.id == d.id
        details = d
        findViewById<TextView>(R.id.title).text = d.title
        setText(R.id.original_title, d.originalTitle)
        setText(R.id.meta, d.meta)
        setText(R.id.ratings, d.ratings)
        description.text = d.description
        if (d.poster.isNotEmpty()) Images.art(findViewById<ImageView>(R.id.art), d.poster, intent.getStringExtra(EXTRA_POSTER))

        // Выбор озвучки на этой странице сохраняем, иначе — та, что смотрели последней
        val chosen = translator?.takeIf { sameTitle && d.translators.contains(it) }
        if (chosen == null || chosen.id == d.position?.translatorId) {
            translator = d.translators.find { it.id == d.position?.translatorId } ?: d.translators.firstOrNull()
            episodes = d.episodes
        }
        updateButtons()
    }

    /** Главная кнопка: какую серию играть, подпись и нужна ли рядом «Следующая». */
    private class Primary(val episode: Episode?, val label: String, val next: Episode?)

    private fun primary(): Primary {
        val d = details ?: return Primary(null, getString(R.string.play), null)
        if (!d.isSeries) {
            val entry = resume.get(d.id, 0, 0)?.takeIf { !it.finished && it.positionMs > 0 }
            return Primary(null, entry?.let { remaining(getString(R.string.resume), it) } ?: getString(R.string.play), null)
        }
        val current = d.currentEpisode?.let { EpisodeNav.find(episodes, it.season, it.episode) } ?: episodes.firstOrNull()
            ?: return Primary(null, getString(R.string.play), null)
        val entry = resume.get(d.id, current.season, current.episode)
        val next = EpisodeNav.next(episodes, current.season, current.episode)
        return when {
            // Серию досмотрели — главная кнопка сразу ведёт к следующей
            entry?.finished == true && next != null -> Primary(next, getString(R.string.play_episode, next.code), null)
            current == episodes.first() && entry == null -> Primary(current, getString(R.string.play_episode, current.code), null)
            entry != null && entry.positionMs > 0 ->
                Primary(current, remaining(getString(R.string.resume_episode, current.code), entry), next)
            else -> Primary(current, getString(R.string.resume_episode, current.code), next)
        }
    }

    private fun updateButtons() {
        val d = details ?: return
        val primary = primary()
        playButton.text = primary.label
        nextButton.text = primary.next?.let { getString(R.string.next_episode_button, it.code) }
        nextButton.visibility = if (primary.next != null) View.VISIBLE else View.GONE
        episodesButton.visibility = if (d.isSeries) View.VISIBLE else View.GONE
        translatorButton.text = getString(R.string.translator_button, translator?.name.orEmpty())
        translatorButton.visibility = if (d.translators.size > 1) View.VISIBLE else View.GONE
        // Для фильмов: у сериала «просмотрено» неоднозначно, а кнопок у него и так много
        watchedButton.setText(if (watched.isWatched(d.id)) R.string.unmark_watched else R.string.mark_watched)
        watchedButton.visibility = if (d.isSeries) View.GONE else View.VISIBLE
    }

    private fun remaining(label: String, entry: ResumeStore.Entry): String {
        val minutes = (entry.remainingMs / 60_000).toInt().coerceAtLeast(1)
        return label + "  ·  " + getString(R.string.remaining_min, minutes)
    }

    private fun playPrimary() {
        val d = details ?: return
        if (d.isSeries && episodes.isEmpty()) return
        val target = primary().episode
        // «Продолжить» туда же, где и сайт: плеер ещё раз сверится с сайтом и со своим последним просмотром
        // (могли досмотреть на другом устройстве, пока открыта эта страница)
        if (target == d.currentEpisode && translator?.id == d.position?.translatorId) {
            startActivity(PlayerActivity.intent(this, d.url, d.title, d.poster))
        } else {
            play(target, fromStart = false)
        }
    }

    private fun play(episode: Episode?, fromStart: Boolean) {
        val d = details ?: return
        val t = translator ?: return
        startActivity(
            PlayerActivity.intent(this, d.url, d.title, d.poster, t.id, episode?.season ?: 0, episode?.episode ?: 0, fromStart),
        )
    }

    private fun showEpisodes() {
        val d = details ?: return
        val current = d.currentEpisode
        episodesPanel.show(d.title, episodes, current, label = { e ->
            when {
                e == current -> "▶  " + e.name
                resume.get(d.id, e.season, e.episode)?.finished == true -> e.name + "  ✓"
                else -> e.name
            }
        }) { play(it, fromStart = false) }
    }

    private fun showTranslators() {
        val d = details ?: return
        val index = d.translators.indexOf(translator)
        choicePanel.show(getString(R.string.translators), d.translators.map { it.name }, index) { i ->
            val chosen = d.translators[i]
            translator = chosen
            if (d.isSeries && chosen.id != d.position?.translatorId) {
                scope.launch {
                    loader.load { client.translatorEpisodes(d, chosen) }?.takeIf { it.isNotEmpty() }?.let {
                        if (translator == chosen) {
                            episodes = it
                            updateButtons()
                        }
                    }
                }
            } else {
                episodes = d.episodes
            }
            updateButtons()
        }
    }

    private fun setText(id: Int, text: String?) {
        val view = findViewById<TextView>(id)
        view.text = text
        view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}
