package dev.alaev.rezkatv.rezka.login

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object Qr {

    /** QR-код с тихой зоной в 4 модуля; модуль — целое число пикселей, чтобы не было размытия. */
    fun bitmap(text: String, maxSizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to QUIET_ZONE,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val modules = matrix.width
        val scale = (maxSizePx / modules).coerceAtLeast(1)
        val size = modules * scale
        val pixels = IntArray(size * size) { i ->
            if (matrix[(i % size) / scale, (i / size) / scale]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    private const val QUIET_ZONE = 4
}

/** Адрес телевизора в домашней сети, на котором его увидит телефон. */
object LanAddress {

    fun find(): Inet4Address? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        return pick(interfaces.filter { runCatching { it.isUp }.getOrDefault(false) }
            .flatMap { nif -> nif.inetAddresses.toList().map { nif.name to it } })
    }

    /** Wi-Fi (wlan*) важнее Ethernet, остальное — в последнюю очередь; только обычные локальные IPv4. */
    fun pick(candidates: List<Pair<String, InetAddress>>): Inet4Address? =
        candidates
            .filter { (_, a) -> a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress && a.isSiteLocalAddress }
            .minByOrNull { (name, _) ->
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    else -> 2
                }
            }?.second as Inet4Address?
}
