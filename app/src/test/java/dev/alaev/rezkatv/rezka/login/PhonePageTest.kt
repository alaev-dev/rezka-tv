package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test

class PhonePageTest {

    @Test
    fun servesPageWithoutHtmlComments() {
        val marker = "<!--"
        assertThat("страница телефона уходит без HTML-комментариев", PhonePage.HTML, not(containsString(marker)))
    }

    @Test
    fun servesScriptWithoutComments() {
        val comments = Regex("(?<!:)//|/\\*").findAll(PhonePage.HTML).map { it.value }.toList()
        assertThat("скрипт без // и /* */, в том числе после кода", comments, empty())
    }
}
