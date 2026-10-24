package dev.alaev.rezkatv

import android.view.KeyEvent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class PlayerKeysTest {

    @Test
    fun handlesPlayPauseOutsidePlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, playerMode = false)
        assertThat("медиаклавиши работают и вне плеера", handled)
    }

    @Test
    fun handlesFastForwardOutsidePlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, playerMode = false)
        assertThat("перемотка с пульта и вне плеера", handled)
    }

    @Test
    fun leavesArrowsToPageOutsidePlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_DPAD_LEFT, playerMode = false)
        assertThat("на сайте стрелки двигают фокус страницы", !handled)
    }

    @Test
    fun leavesBackToPageOutsidePlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_BACK, playerMode = false)
        assertThat("на сайте «Назад» — назад по страницам", !handled)
    }

    @Test
    fun takesArrowsInPlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_DPAD_LEFT, playerMode = true)
        assertThat("в плеере стрелки — перемотка", handled)
    }

    @Test
    fun takesBackInPlayer() {
        val handled = PlayerKeys.handles(KeyEvent.KEYCODE_BACK, playerMode = true)
        assertThat("в плеере «Назад» закрывает плеер", handled)
    }

    @Test
    fun togglesPlaybackOnOk() {
        val expected = PlayerCommand("toggle")
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_CENTER, 0)
        assertThat("OK — пауза и продолжение", command, equalTo(expected))
    }

    @Test
    fun exitsPlayerOnBack() {
        val expected = PlayerCommand("exit")
        val command = PlayerKeys.command(KeyEvent.KEYCODE_BACK, 0)
        assertThat("«Назад» — выход из плеера", command, equalTo(expected))
    }

    @Test
    fun ignoresHeldOk() {
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_CENTER, 1)
        assertThat("удержание OK не переключает паузу снова", command, nullValue())
    }

    @Test
    fun ignoresHeldBack() {
        val command = PlayerKeys.command(KeyEvent.KEYCODE_BACK, 4)
        assertThat("удержание «Назад» срабатывает один раз", command, nullValue())
    }

    @Test
    fun seeksForwardOnRight() {
        val expected = PlayerCommand("seek", 10)
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        assertThat("вправо — вперёд на шаг", command, equalTo(expected))
    }

    @Test
    fun seeksBackwardOnLeft() {
        val expected = PlayerCommand("seek", -10)
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_LEFT, 0)
        assertThat("влево — назад на шаг", command, equalTo(expected))
    }

    @Test
    fun seeksFasterWhileRightIsHeld() {
        val expected = PlayerCommand("seek", 30)
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 20)
        assertThat("чем дольше держишь, тем больше шаг", command, equalTo(expected))
    }

    @Test
    fun seeksFarBackOnLongRewindHold() {
        val expected = PlayerCommand("seek", -60)
        val command = PlayerKeys.command(KeyEvent.KEYCODE_MEDIA_REWIND, 60)
        assertThat("долгое удержание перемотки назад — минута", command, equalTo(expected))
    }

    @Test
    fun skipsFirstSeekRepeat() {
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 1)
        assertThat("повторы реже: первый пропускается", command, nullValue())
    }

    @Test
    fun skipsSeekRepeatBetweenSteps() {
        val command = PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        assertThat("берётся каждый четвёртый повтор", command, nullValue())
    }

    @Test
    fun buildsJavaScriptCallForPage() {
        val expected = "window.__tv&&__tv.cmd('seek',-10)"
        val js = PlayerCommand("seek", -10).toJs()
        assertThat("команда уходит в tv.js и не падает, если его нет", js, equalTo(expected))
    }
}
