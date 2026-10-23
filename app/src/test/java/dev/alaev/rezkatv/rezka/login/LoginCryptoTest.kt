package dev.alaev.rezkatv.rezka.login

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec

class LoginCryptoTest {

    /** Зашифровано функцией seal() из docs/login/index.html (WebCrypto в Node) — проверка совместимости. */
    private val fixture = JSONObject(requireNotNull(javaClass.getResource("/login/sealed.json")).readText())

    private fun tvCrypto(): LoginCrypto {
        val private = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(LoginCrypto.unb64(fixture.getString("tvPrivatePkcs8")))) as ECPrivateKey
        val point = LoginCrypto.decodePoint(LoginCrypto.unb64(fixture.getString("tvPublic")))
        val public = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, private.params))
        return LoginCrypto(KeyPair(public, private))
    }

    private fun decrypt(crypto: LoginCrypto = tvCrypto(), phoneKey: String = fixture.getString("p"),
                        iv: String = fixture.getString("iv"), c: String = fixture.getString("c"),
                        token: String = fixture.getString("token")) = crypto.decrypt(phoneKey, iv, c, token)

    @Test
    fun decryptsWhatThePhonePageSealed() {
        assertEquals(fixture.getString("tvPublic"), tvCrypto().publicKey)
        val credentials = requireNotNull(decrypt())
        assertEquals("tv@example.com", credentials.email)
        assertEquals("пароль с пробелом & знаками=+%", credentials.password)
    }

    @Test
    fun otherTokenOrKeyOrTamperingFails() {
        assertNull(decrypt(token = "0123456789abcdef0123456789abcdee"))
        assertNull(decrypt(crypto = LoginCrypto()))
        val c = fixture.getString("c")
        assertNull(decrypt(c = (if (c[0] == 'A') "B" else "A") + c.substring(1)))
        assertNull(decrypt(phoneKey = fixture.getString("tvPublic")))
    }

    @Test
    fun garbageIsRejectedNotThrown() {
        assertNull(decrypt(phoneKey = ""))
        assertNull(decrypt(phoneKey = "!!!"))
        assertNull(decrypt(iv = ""))
        assertNull(decrypt(c = ""))
        // Точка не на кривой P-256 (атака неверной кривой)
        val bad = LoginCrypto.unb64(fixture.getString("p")).also { it[64] = (it[64] + 1).toByte() }
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
