package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class LanAddressTest {

    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun prefersWifiOverEthernet() {
        val wifi = ip("192.168.1.20")
        val interfaces = listOf(
            "lo" to ip("127.0.0.1"),
            "eth0" to ip("10.0.0.5"),
            "wlan0" to ip("fe80::1"),
            "wlan0" to wifi,
        )
        assertThat("Wi-Fi важнее Ethernet, IPv6 пропускается", LanAddress.pick(interfaces), equalTo(wifi))
    }

    @Test
    fun prefersEthernetOverOtherInterfaces() {
        val ethernet = ip("10.0.0.5")
        val interfaces = listOf("p2p0" to ip("172.16.0.2"), "eth0" to ethernet)
        val picked = LanAddress.pick(interfaces)
        assertThat("без Wi-Fi — Ethernet, остальное в последнюю очередь", picked, equalTo(ethernet))
    }

    @Test
    fun ignoresLoopbackLinkLocalAndPublicAddresses() {
        val interfaces = listOf("lo" to ip("127.0.0.1"), "wlan0" to ip("169.254.1.1"), "wlan0" to ip("8.8.8.8"))
        assertThat("только обычные локальные IPv4", LanAddress.pick(interfaces), nullValue())
    }
}
