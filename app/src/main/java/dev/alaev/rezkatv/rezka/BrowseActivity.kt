package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.leanback.widget.BrowseFrameLayout
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import dev.alaev.rezkatv.R
import dev.alaev.rezkatv.rezka.lists.ListsAssets
import dev.alaev.rezkatv.rezka.lists.OscarPage
import dev.alaev.rezkatv.rezka.lists.TitleMarks
import dev.alaev.rezkatv.rezka.lists.TopPage

/** Главная rezka: меню слева, справа — главная с рядами, раздел-сетка, «Рейтинг» или «Оскар». */
class BrowseActivity : Activity() {

    private val scope = MainScope()
    private lateinit var client: RezkaClient
    private lateinit var settings: Settings
    private lateinit var rail: NavRail
    private lateinit var home: HomePage
    private lateinit var grid: GridPage
    private lateinit var top: TopPage
    private lateinit var oscar: OscarPage
    private lateinit var choice: ChoicePanel
    private lateinit var root: BrowseFrameLayout

    // Что было при создании экрана: если в настройках поменяли — пересоздаём
    private var liteMode = false
    private var baseUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_BASE)?.let { RezkaClient.setBaseUrl(this, it) }
        settings = Settings(this)
        client = RezkaClient.get(this)
        liteMode = settings.liteMode
        baseUrl = client.baseUrl
        setContentView(R.layout.activity_browse)

        root = findViewById(R.id.root)
        val loader = Loader(this, findViewById<TextView>(R.id.status))
        home = HomePage(this, client, scope, loader, ResumeStore(this), liteMode)
        grid = GridPage(this, client, scope, loader)
        choice = ChoicePanel(findViewById(R.id.choice_panel))
        val marks = TitleMarks(this, client)
        top = TopPage(this, client, scope, marks, choice)
        oscar = OscarPage(this, client, scope, marks)
        // Списки разбираются в фоне, пока пользователь выбирает раздел в меню
        rail = NavRail(findViewById(R.id.rail), findViewById(R.id.rail_scrim), ::onSection) { ListsAssets.preload(this) }

        root.onFocusSearchListener = BrowseFrameLayout.OnFocusSearchListener { focused, direction ->
            when {
                // Панель выбора открыта — фокус не уходит из неё на страницу под ней
                choice.isShowing && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) -> focused
                // Влево с первой карточки — в меню, на текущий раздел (а не на ближайший пункт)
                direction == View.FOCUS_LEFT && !rail.contains(focused) -> {
                    val next = FocusFinder.getInstance().findNextFocus(root, focused, direction)
                    if (next == null || rail.contains(next)) rail.selectedView() else null
                }
                direction == View.FOCUS_RIGHT && rail.contains(focused) -> {
                    focusPage()
                    root.findFocus()?.takeUnless { rail.contains(it) }
                }
                else -> null
            }
        }

        home.load()
    }

    override fun onRestart() {
        super.onRestart()
        // Вернулись из плеера или после входа: прогресс в «Досмотреть» мог измениться
        if (home.isEmpty) home.load() else home.refresh(Shelf.CONTINUE)
        // …и отметки «просмотрено» в списках
        if (top.isVisible) top.refresh()
        if (oscar.isVisible) oscar.refresh()
    }

    override fun onResume() {
        super.onResume()
        // Настройки открываются поверх (прозрачной панелью), поэтому проверяем здесь, а не в onRestart
        if (settings.liteMode != liteMode || RezkaClient.baseUrl(this) != baseUrl) recreate()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        // Выход из rezka: процесс :web больше не нужен, отдаём память системе
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) home.onUserKey()
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            choice.isShowing -> choice.hide()
            rail.contains(currentFocus) -> if (rail.selected != Section.HOME) onSection(Section.HOME) else finish()
            home.isVisible && home.onBack() -> Unit
            grid.isVisible && grid.onBack() -> Unit
            top.isVisible && top.onBack() -> Unit
            oscar.isVisible && oscar.onBack() -> Unit
            else -> rail.focus()
        }
    }

    private fun onSection(section: Section) {
        when (section) {
            Section.SEARCH -> openSearch()
            Section.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> {
                rail.selected = section
                if (section != Section.HOME) home.hide()
                if (section.category == null) grid.hide()
                if (section != Section.TOP) top.hide()
                if (section != Section.OSCAR) oscar.hide()
                when (section) {
                    Section.HOME -> home.show()
                    Section.TOP -> top.show()
                    Section.OSCAR -> oscar.show()
                    else -> grid.show(requireNotNull(section.category))
                }
                focusPage()
            }
        }
    }

    private fun focusPage() {
        when {
            grid.isVisible -> grid.focus()
            top.isVisible -> top.focus()
            oscar.isVisible -> oscar.focus()
            else -> home.focus()
        }
    }

    private companion object {
        const val EXTRA_BASE = "base"
    }
}
