package dev.alaev.rezkatv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {

    @Test
    fun mediaKeysAlwaysHandled() {
        assertTrue(PlayerKeys.handles(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, playerMode = false))
        assertTrue(PlayerKeys.handles(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, playerMode = false))
    }

    @Test
    fun dpadOnlyInPlayerMode() {
        assertFalse(PlayerKeys.handles(KeyEvent.KEYCODE_DPAD_LEFT, playerMode = false))
        assertFalse(PlayerKeys.handles(KeyEvent.KEYCODE_BACK, playerMode = false))
        assertTrue(PlayerKeys.handles(KeyEvent.KEYCODE_DPAD_LEFT, playerMode = true))
        assertTrue(PlayerKeys.handles(KeyEvent.KEYCODE_BACK, playerMode = true))
    }

    @Test
    fun okTogglesAndBackExits() {
        assertEquals(PlayerCommand("toggle"), PlayerKeys.command(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(PlayerCommand("exit"), PlayerKeys.command(KeyEvent.KEYCODE_BACK, 0))
    }

    @Test
    fun heldNonSeekKeysFireOnce() {
        assertNull(PlayerKeys.command(KeyEvent.KEYCODE_DPAD_CENTER, 1))
        assertNull(PlayerKeys.command(KeyEvent.KEYCODE_BACK, 4))
    }

    @Test
    fun seekDirectionAndAcceleration() {
        assertEquals(PlayerCommand("seek", 10), PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 0))
        assertEquals(PlayerCommand("seek", -10), PlayerKeys.command(KeyEvent.KEYCODE_DPAD_LEFT, 0))
        assertEquals(PlayerCommand("seek", 30), PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 20))
        assertEquals(PlayerCommand("seek", -60), PlayerKeys.command(KeyEvent.KEYCODE_MEDIA_REWIND, 60))
    }

    @Test
    fun seekRepeatsAreThrottled() {
        assertNull(PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 1))
        assertNull(PlayerKeys.command(KeyEvent.KEYCODE_DPAD_RIGHT, 3))
    }

    @Test
    fun jsCall() {
        assertEquals("window.__tv&&__tv.cmd('seek',-10)", PlayerCommand("seek", -10).toJs())
    }
}
