package app.sinshield

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DnsUpstreamProviderTest {
    @Test
    fun currentReadsDnsServersAgainAfterNetworkChanges() {
        var networkDns = listOf(address("192.168.1.1"))
        val provider = DnsUpstreamProvider(
            networkDnsServers = { networkDns },
            fallbackDnsServers = listOf(address("1.1.1.1"), address("8.8.8.8")),
            excludedAddress = "10.77.0.2"
        )

        assertEquals(
            listOf("192.168.1.1", "1.1.1.1", "8.8.8.8"),
            provider.current().mapNotNull(InetAddress::getHostAddress)
        )

        networkDns = listOf(address("10.20.30.40"))

        assertEquals(
            listOf("10.20.30.40", "1.1.1.1", "8.8.8.8"),
            provider.current().mapNotNull(InetAddress::getHostAddress)
        )
        assertFalse(provider.current().any { it.hostAddress == "192.168.1.1" })
    }

    @Test
    fun currentExcludesVirtualDnsIpv6AndDuplicates() {
        val provider = DnsUpstreamProvider(
            networkDnsServers = {
                listOf(
                    address("10.77.0.2"),
                    address("2001:4860:4860::8888"),
                    address("1.1.1.1")
                )
            },
            fallbackDnsServers = listOf(address("1.1.1.1"), address("8.8.8.8")),
            excludedAddress = "10.77.0.2"
        )

        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            provider.current().mapNotNull(InetAddress::getHostAddress)
        )
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
