package com.example.sinshield

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketCodecTest {
    @Test
    fun extractsQuestionAndCreatesNxdomainResponse() {
        val query = dnsQuery("sub.adult-site.test")

        assertEquals("sub.adult-site.test", DnsMessageCodec.queryName(query))
        val response = DnsMessageCodec.nxdomainResponse(query)
        assertNotNull(response)
        response!!

        assertEquals(query[0], response[0])
        assertEquals(query[1], response[1])
        assertTrue(response[2].toInt() and 0x80 != 0)
        assertEquals(3, response[3].toInt() and 0x0f)
        assertEquals(0, unsignedShort(response, 6))
        assertEquals(0, unsignedShort(response, 8))
        assertEquals(0, unsignedShort(response, 10))
        assertEquals("sub.adult-site.test", DnsMessageCodec.queryName(response))
    }

    @Test
    fun rejectsMalformedDnsMessages() {
        assertNull(DnsMessageCodec.queryName(byteArrayOf(1, 2, 3)))
        assertNull(DnsMessageCodec.queryName(ByteArray(12)))
        val pointerLoop = ByteArray(18).apply {
            this[5] = 1
            this[12] = 0xc0.toByte()
            this[13] = 12
        }
        assertNull(DnsMessageCodec.queryName(pointerLoop))
    }

    @Test
    fun buildsAValidReversedIpv4UdpResponse() {
        val request = Ipv4UdpDatagram(
            sourceAddress = byteArrayOf(10, 77, 0, 1),
            destinationAddress = byteArrayOf(10, 77, 0, 2),
            sourcePort = 49_123,
            destinationPort = 53,
            payload = dnsQuery("adult-site.test")
        )
        val dnsResponse = DnsMessageCodec.nxdomainResponse(request.payload)!!

        val packet = Ipv4UdpPacketCodec.response(request, dnsResponse, 42)
        val parsed = Ipv4UdpPacketCodec.parse(packet, packet.size)

        assertNotNull(parsed)
        assertArrayEquals(request.destinationAddress, parsed!!.sourceAddress)
        assertArrayEquals(request.sourceAddress, parsed.destinationAddress)
        assertEquals(53, parsed.sourcePort)
        assertEquals(49_123, parsed.destinationPort)
        assertArrayEquals(dnsResponse, parsed.payload)
        assertEquals(0, internetChecksum(packet, 0, 20))
    }

    private fun dnsQuery(hostname: String): ByteArray {
        val labels = hostname.split('.')
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        return ByteArray(size).also { query ->
            query[0] = 0x12
            query[1] = 0x34
            query[2] = 0x01
            query[5] = 0x01
            var offset = 12
            labels.forEach { label ->
                query[offset++] = label.length.toByte()
                label.encodeToByteArray().copyInto(query, offset)
                offset += label.length
            }
            query[offset++] = 0
            query[offset++] = 0
            query[offset++] = 1
            query[offset++] = 0
            query[offset] = 1
        }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun internetChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index < offset + length) {
            sum += unsignedShort(bytes, index)
            index += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }
}
