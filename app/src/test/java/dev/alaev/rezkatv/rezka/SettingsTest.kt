package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {

    private val all = listOf("360p", "480p", "720p", "1080p", "1080p Ultra").map { Quality(it, listOf("https://x/$it")) }

    @Test
    fun autoIsBestRegularQualityForScreen() {
        assertEquals("1080p", QualityPicker.pick(all, Settings.AUTO, 1080)?.label)
        assertEquals("720p", QualityPicker.pick(all, Settings.AUTO, 720)?.label)
        assertEquals("1080p", QualityPicker.pick(all, Settings.AUTO, 2160)?.label) // Ultra только по явному выбору
    }

    @Test
    fun explicitChoiceOrNearestLower() {
        assertEquals("1080p Ultra", QualityPicker.pick(all, "1080p Ultra", 1080)?.label)
        assertEquals("480p", QualityPicker.pick(all, "480p", 1080)?.label)
        val noHd = all.take(2)
        assertEquals("480p", QualityPicker.pick(noHd, "1080p", 1080)?.label)
        assertEquals("1080p", QualityPicker.pick(all.take(4), "1080p Ultra", 1080)?.label)
    }

    @Test
    fun fallsBackToLowestWhenNothingFits() {
        val hdOnly = all.drop(3)
        assertEquals("1080p", QualityPicker.pick(hdOnly, "360p", 1080)?.label)
        assertNull(QualityPicker.pick(emptyList(), Settings.AUTO, 1080))
    }

    @Test
    fun seekStepMapping() {
        assertEquals(10_000, Settings.seekStepMs(null))
        assertEquals(30_000, Settings.seekStepMs("30"))
        assertEquals(5_000, Settings.seekStepMs("5"))
        assertEquals(10_000, Settings.seekStepMs("abc"))
        assertEquals(10_000, Settings.seekStepMs("0"))
    }
}
