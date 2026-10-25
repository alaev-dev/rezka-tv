package dev.alaev.rezkatv.rezka

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class SettingsTest {

    private fun qualities(vararg labels: String) = labels.map { Quality(it, listOf("https://x/$it")) }

    private fun all() = qualities("360p", "480p", "720p", "1080p", "1080p Ultra")

    @Test
    fun picksBestRegularQualityForFullHdScreen() {
        val expected = "1080p"
        val picked = QualityPicker.pick(all(), Settings.AUTO, 1080)?.label
        assertThat("«Авто» на 1080 — лучшее обычное", picked, equalTo(expected))
    }

    @Test
    fun picksQualityNotAboveScreen() {
        val expected = "720p"
        val picked = QualityPicker.pick(all(), Settings.AUTO, 720)?.label
        assertThat("«Авто» не выше экрана", picked, equalTo(expected))
    }

    @Test
    fun skipsUltraInAutoEvenOnLargeScreen() {
        val expected = "1080p"
        val picked = QualityPicker.pick(all(), Settings.AUTO, 2160)?.label
        assertThat("Ultra — только по явному выбору", picked, equalTo(expected))
    }

    @Test
    fun picksExplicitUltra() {
        val expected = "1080p Ultra"
        val picked = QualityPicker.pick(all(), expected, 1080)?.label
        assertThat("явный выбор Ultra соблюдается", picked, equalTo(expected))
    }

    @Test
    fun picksExplicitLowQuality() {
        val expected = "480p"
        val picked = QualityPicker.pick(all(), expected, 1080)?.label
        assertThat("явный выбор ниже экрана соблюдается", picked, equalTo(expected))
    }

    @Test
    fun fallsBackToNearestLowerWhenChoiceIsMissing() {
        val expected = "480p"
        val picked = QualityPicker.pick(qualities("360p", "480p"), "1080p", 1080)?.label
        assertThat("нет выбранного — ближайшее ниже", picked, equalTo(expected))
    }

    @Test
    fun fallsBackFromMissingUltraToRegularFullHd() {
        val expected = "1080p"
        val picked = QualityPicker.pick(qualities("360p", "480p", "720p", "1080p"), "1080p Ultra", 1080)?.label
        assertThat("нет Ultra — обычное 1080p", picked, equalTo(expected))
    }

    @Test
    fun fallsBackToLowestWhenNothingIsLower() {
        val expected = "1080p"
        val picked = QualityPicker.pick(qualities("1080p", "1080p Ultra"), "360p", 1080)?.label
        assertThat("ниже выбранного ничего нет — самое низкое из имеющихся", picked, equalTo(expected))
    }

    @Test
    fun picksNothingFromEmptyList() {
        val picked = QualityPicker.pick(emptyList(), Settings.AUTO, 1080)
        assertThat("без качеств выбирать нечего", picked, nullValue())
    }

    @Test
    fun usesTenSecondSeekStepByDefault() {
        val expected = 10_000L
        val step = Settings.seekStepMs(null)
        assertThat("шаг перемотки по умолчанию — 10 с", step, equalTo(expected))
    }

    @Test
    fun convertsSeekStepSettingToMilliseconds() {
        val expected = 30_000L
        val step = Settings.seekStepMs("30")
        assertThat("настройка в секундах", step, equalTo(expected))
    }

    @Test
    fun acceptsShortSeekStep() {
        val expected = 5_000L
        val step = Settings.seekStepMs("5")
        assertThat("короткий шаг 5 с", step, equalTo(expected))
    }

    @Test
    fun ignoresUnparsableSeekStep() {
        val expected = 10_000L
        val step = Settings.seekStepMs("abc")
        assertThat("не число — шаг по умолчанию", step, equalTo(expected))
    }

    @Test
    fun ignoresZeroSeekStep() {
        val expected = 10_000L
        val step = Settings.seekStepMs("0")
        assertThat("ноль — шаг по умолчанию", step, equalTo(expected))
    }
}
