package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec

class LoginCryptoTest {

    private fun fixture() = JSONObject(requireNotNull(javaClass.getResource("/login/sealed.json")).readText())

    private fun tvCrypto(): LoginCrypto {
        val pkcs8 = LoginCrypto.unb64(fixture().getString("tvPrivatePkcs8"))
        val private = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as ECPrivateKey
        val point = LoginCrypto.decodePoint(LoginCrypto.unb64(fixture().getString("tvPublic")))
        val public = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, private.params))
        return LoginCrypto(KeyPair(public, private))
    }

    private fun decrypt(
        crypto: LoginCrypto = tvCrypto(),
        phoneKey: String = fixture().getString("p"),
        iv: String = fixture().getString("iv"),
        c: String = fixture().getString("c"),
        token: String = fixture().getString("token"),
    ) = crypto.decrypt(phoneKey, iv, c, token)

    @Test
    fun restoresTvKeyPairFromFixture() {
        val expected = fixture().getString("tvPublic")
        assertThat("ключ телевизора из фикстуры восстановлен", tvCrypto().publicKey, equalTo(expected))
    }

    @Test
    fun decryptsEmailSealedByPhonePage() {
        val expected = "tv@example.com"
        val credentials = decrypt()
        assertThat("email, зашифрованный seal() страницы входа (WebCrypto)", credentials?.email, equalTo(expected))
    }

    @Test
    fun decryptsPasswordWithSpacesAndSpecialCharacters() {
        val expected = "пароль с пробелом & знаками=+%"
        val credentials = decrypt()
        assertThat("пароль с пробелами и спецсимволами цел", credentials?.password, equalTo(expected))
    }

    @Test
    fun rejectsOtherToken() {
        val token = "0123456789abcdef0123456789abcdee"
        assertThat("токен — дополнительные данные AES-GCM: чужой не подходит", decrypt(token = token), nullValue())
    }

    @Test
    fun rejectsOtherTvKey() {
        val otherTv = LoginCrypto()
        assertThat("шифротекст для другого телевизора не расшифровать", decrypt(crypto = otherTv), nullValue())
    }

    @Test
    fun rejectsTamperedCiphertext() {
        val c = fixture().getString("c")
        val tampered = (if (c[0] == 'A') "B" else "A") + c.substring(1)
        assertThat("изменённый шифротекст отвергается", decrypt(c = tampered), nullValue())
    }

    @Test
    fun rejectsTvKeyPassedAsPhoneKey() {
        val tvKey = fixture().getString("tvPublic")
        assertThat("ключ телевизора вместо ключа телефона не подходит", decrypt(phoneKey = tvKey), nullValue())
    }

    @Test
    fun garbageIsRejectedNotThrown() {
        assertNull(decrypt(phoneKey = ""))
        assertNull(decrypt(phoneKey = "!!!"))
        assertNull(decrypt(iv = ""))
        assertNull(decrypt(c = ""))
        val bad = LoginCrypto.unb64(fixture().getString("p")).also { it[64] = (it[64] + 1).toByte() }
        assertNull(decrypt(phoneKey = LoginCrypto.b64(bad)))
    }

    @Test
    fun publicKeyIsRawUncompressedPoint() {
        val raw = LoginCrypto.unb64(LoginCrypto().publicKey)
        assertEquals(65, raw.size)
        assertEquals(4.toByte(), raw[0])
        assertEquals(87, LoginCrypto().publicKey.length)
    }

    @Test
    fun passwordIsNotInToString() {
        assertFalse(LoginCrypto.Credentials("a@b.c", "secret").toString().contains("secret"))
    }
}
