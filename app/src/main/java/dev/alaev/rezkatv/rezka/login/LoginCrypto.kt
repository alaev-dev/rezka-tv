package dev.alaev.rezkatv.rezka.login

import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LoginCrypto(private val keys: KeyPair = newKeyPair()) {

    val publicKey: String = b64(encodePoint((keys.public as ECPublicKey).w))

    fun decrypt(phoneKey: String, iv: String, ciphertext: String, token: String): Credentials? = runCatching {
        val params = (keys.public as ECPublicKey).params
        val point = decodePoint(unb64(phoneKey))
        val phone = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keys.private)
        agreement.doPhase(phone, true)
        val secret = agreement.generateSecret()
        val key = MessageDigest.getInstance("SHA-256").digest(secret + LABEL.toByteArray())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, unb64(iv)))
        cipher.updateAAD(token.toByteArray())
        val json = JSONObject(String(cipher.doFinal(unb64(ciphertext)), Charsets.UTF_8))
        Credentials(json.getString("email"), json.getString("password"))
    }.getOrNull()

    data class Credentials(val email: String, val password: String) {
        override fun toString() = "Credentials(email=$email, password=***)"
    }

    companion object {
        const val LABEL = "rezka-tv login v1"
        private const val TAG_BITS = 128
        private const val SIZE = 32

        private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
        private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)

        fun newKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        fun encodePoint(w: ECPoint): ByteArray = byteArrayOf(4) + unsigned(w.affineX) + unsigned(w.affineY)

        fun decodePoint(raw: ByteArray): ECPoint {
            require(raw.size == 1 + 2 * SIZE && raw[0] == 4.toByte()) { "bad point" }
            val x = BigInteger(1, raw.copyOfRange(1, 1 + SIZE))
            val y = BigInteger(1, raw.copyOfRange(1 + SIZE, raw.size))
            require(x < P && y < P) { "bad point" }
            val right = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(B).mod(P)
            require(y.pow(2).mod(P) == right) { "point not on curve" }
            return ECPoint(x, y)
        }

        private fun unsigned(n: BigInteger): ByteArray {
            val bytes = n.toByteArray()
            return when {
                bytes.size == SIZE -> bytes
                bytes.size > SIZE -> bytes.copyOfRange(bytes.size - SIZE, bytes.size)
                else -> ByteArray(SIZE - bytes.size) + bytes
            }
        }

        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
    }
}
