package dev.alaev.rezkatv.rezka.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class LanAddressTest {

    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun prefersWifiThenEthernet() {
        val picked = LanAddress.pick(
            listOf("lo" to ip("127.0.0.1"), "eth0" to ip("10.0.0.5"), "wlan0" to ip("fe80::1"), "wlan0" to ip("192.168.1.20")),
        )
        assertEquals(ip("192.168.1.20"), picked)
        assertEquals(ip("10.0.0.5"), LanAddress.pick(listOf("p2p0" to ip("172.16.0.2"), "eth0" to ip("10.0.0.5"))))
    }

    @Test
    fun ignoresLoopbackLinkLocalAndPublic() {
        assertNull(LanAddress.pick(listOf("lo" to ip("127.0.0.1"), "wlan0" to ip("169.254.1.1"), "wlan0" to ip("8.8.8.8"))))
    }
}
