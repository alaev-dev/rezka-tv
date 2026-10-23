package dev.alaev.rezkatv

import android.view.KeyEvent

data class PlayerCommand(val name: String, val arg: Int = 0) {
    fun toJs() = "window.__tv&&__tv.cmd('$name',$arg)"
}

/** Кнопки пульта → команды плееру в tv.js. Стрелки и OK перехватываются только в режиме плеера. */
object PlayerKeys {

    private val MEDIA_KEYS = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
    )

    private val PLAYER_KEYS = setOf(
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_BACK,
    )

    fun handles(keyCode: Int, playerMode: Boolean) =
        keyCode in MEDIA_KEYS || (playerMode && keyCode in PLAYER_KEYS)

    fun command(keyCode: Int, repeatCount: Int): PlayerCommand? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
            seekStep(repeatCount)?.let { PlayerCommand("seek", it) }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND ->
            seekStep(repeatCount)?.let { PlayerCommand("seek", -it) }
        else -> if (repeatCount > 0) null else when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                PlayerCommand("toggle")
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerCommand("play")
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> PlayerCommand("pause")
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> PlayerCommand("controls")
            KeyEvent.KEYCODE_BACK -> PlayerCommand("exit")
            else -> null
        }
    }

    /**
     * Шаг перемотки в секундах при удержании кнопки; null — пропустить этот повтор.
     * Повторы идут ~20 раз в секунду, берём каждый 4-й, шаг растёт чем дольше держишь.
     */
    fun seekStep(repeatCount: Int): Int? = when {
        repeatCount % 4 != 0 -> null
        repeatCount < 20 -> 10
        repeatCount < 60 -> 30
        else -> 60
    }
}
