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
    fun servesScriptWithoutLineComments() {
        val comments = PhonePage.HTML.lines().filter { it.trimStart().startsWith("//") }
        assertThat("скрипт страницы без комментариев", comments, empty())
    }
}
