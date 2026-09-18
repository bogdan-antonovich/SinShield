package app.sinshield

import java.nio.charset.StandardCharsets

internal object DnsMessageCodec {
    // Fixed 12-byte DNS message header (RFC 1035 §4.1.1): id, flags, and the four section counts.
    // The first question immediately follows it.
    private const val HEADER_SIZE = 12

    fun queryName(message: ByteArray): String? {
        // Offset 4 holds QDCOUNT; a message with no question has no name to read.
        if (message.size < HEADER_SIZE || unsignedShort(message, 4) < 1) return null
        return readName(message, HEADER_SIZE)?.first
    }

    fun nxdomainResponse(query: ByteArray): ByteArray? = errorResponse(query, responseCode = 3)

    fun serverFailureResponse(query: ByteArray): ByteArray? = errorResponse(query, responseCode = 2)

    private fun errorResponse(query: ByteArray, responseCode: Int): ByteArray? {
        if (query.size < HEADER_SIZE) return null
        val questionCount = unsignedShort(query, 4)
        if (questionCount < 1) return null
        var end = HEADER_SIZE
        repeat(questionCount) {
            val name = readName(query, end) ?: return null
            end = name.second
            if (end + 4 > query.size) return null
            end += 4
        }

        // Reuse the query verbatim up to the end of its question section, then rewrite the header
        // into a matching response. This echoes the original id and question, which resolvers
        // require to correlate the reply.
        return query.copyOf(end).also { response ->
            val queryFlags = unsignedShort(query, 2)
            // Flags: set QR (0x8000, response) and RA (0x0080, recursion available); preserve the
            // query's Opcode and RD bits (0x7900); set the RCODE. Everything else is cleared.
            val flags = 0x8000 or (queryFlags and 0x7900) or 0x0080 or responseCode
            putUnsignedShort(response, 2, flags)
            // Zero ANCOUNT/NSCOUNT/ARCOUNT: an error reply carries only the echoed question.
            putUnsignedShort(response, 6, 0)
            putUnsignedShort(response, 8, 0)
            putUnsignedShort(response, 10, 0)
        }
    }

    /** Returns the decoded name and the first byte after its encoded representation. */
    private fun readName(message: ByteArray, start: Int): Pair<String, Int>? {
        if (start !in message.indices) return null
        val labels = mutableListOf<String>()
        // Compression pointers can jump backward; tracking visited targets rejects a pointer loop
        // that would otherwise spin forever. The label cap is a second bound on malformed input.
        val visitedPointers = mutableSetOf<Int>()
        var position = start
        // The encoded name ends at the first terminator or pointer we follow; later jumps read
        // reused labels elsewhere in the message and must not extend the reported end offset.
        var encodedEnd = -1
        var labelCount = 0
        while (position < message.size && labelCount++ < 128) {
            val length = message[position].toInt() and 0xff
            when {
                length == 0 -> {
                    if (encodedEnd < 0) encodedEnd = position + 1
                    return labels.joinToString(".") to encodedEnd
                }
                // Top two bits set (0xc0) mark a compression pointer; the low 14 bits are an
                // offset from the message start to the remainder of the name.
                length and 0xc0 == 0xc0 -> {
                    if (position + 1 >= message.size) return null
                    val pointer = ((length and 0x3f) shl 8) or
                        (message[position + 1].toInt() and 0xff)
                    if (pointer !in message.indices || !visitedPointers.add(pointer)) return null
                    if (encodedEnd < 0) encodedEnd = position + 2
                    position = pointer
                }
                // Labels are capped at 63 bytes (the two high bits being reserved for the pointer
                // marker); anything longer, or a length running past the buffer, is malformed.
                length > 63 || position + 1 + length > message.size -> return null
                else -> {
                    val label = String(
                        message,
                        position + 1,
                        length,
                        StandardCharsets.US_ASCII
                    )
                    // Reject control characters and non-ASCII bytes. Legitimate hostnames are
                    // ASCII (internationalized names arrive punycode-encoded), so this keeps
                    // untrusted, possibly log-injecting bytes out of the matched name.
                    if (label.any { it.code <= 0x20 || it.code >= 0x7f }) return null
                    labels += label.lowercase()
                    position += length + 1
                }
            }
        }
        return null
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}

internal data class Ipv4UdpDatagram(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray
)

internal object Ipv4UdpPacketCodec {
    private const val IPV4_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8
    private const val UDP_PROTOCOL = 17

    fun parse(packet: ByteArray, length: Int): Ipv4UdpDatagram? {
        if (length < IPV4_HEADER_SIZE + UDP_HEADER_SIZE) return null
        // High nibble of byte 0 is the IP version; only IPv4 is handled here.
        if ((packet[0].toInt() ushr 4) != 4) return null
        // Low nibble is IHL, a count of 32-bit words, so the header length is that times four.
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (headerLength < IPV4_HEADER_SIZE || headerLength + UDP_HEADER_SIZE > length) return null
        if ((packet[9].toInt() and 0xff) != UDP_PROTOCOL) return null
        // Drop fragmented datagrams: DF/MF flags plus the fragment offset live in the low 14 bits,
        // and a DNS query the tunnel captures fits in one packet, so reassembly is never needed.
        val fragmentFlagsAndOffset = unsignedShort(packet, 6)
        if (fragmentFlagsAndOffset and 0x3fff != 0) return null
        val totalLength = unsignedShort(packet, 2).coerceAtMost(length)
        val udpLength = unsignedShort(packet, headerLength + 4)
        if (udpLength < UDP_HEADER_SIZE || headerLength + udpLength > totalLength) return null
        return Ipv4UdpDatagram(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = unsignedShort(packet, headerLength),
            destinationPort = unsignedShort(packet, headerLength + 2),
            payload = packet.copyOfRange(
                headerLength + UDP_HEADER_SIZE,
                headerLength + udpLength
            )
        )
    }

    fun response(request: Ipv4UdpDatagram, payload: ByteArray, identification: Int): ByteArray {
        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IPV4_HEADER_SIZE + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = 0x45 // IPv4, IHL 5 (a 20-byte header with no options).
        putUnsignedShort(packet, 2, totalLength)
        putUnsignedShort(packet, 4, identification)
        packet[6] = 0x40 // Set the Don't Fragment flag.
        packet[8] = 64 // TTL.
        packet[9] = UDP_PROTOCOL.toByte()
        // Swap the endpoints so the reply travels from the query's destination back to its source.
        request.destinationAddress.copyInto(packet, 12)
        request.sourceAddress.copyInto(packet, 16)
        putUnsignedShort(packet, IPV4_HEADER_SIZE, request.destinationPort)
        putUnsignedShort(packet, IPV4_HEADER_SIZE + 2, request.sourcePort)
        putUnsignedShort(packet, IPV4_HEADER_SIZE + 4, udpLength)
        payload.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)

        putUnsignedShort(packet, 10, checksum(packet, 0, IPV4_HEADER_SIZE))
        val udpChecksum = udpChecksum(packet, udpLength)
        // RFC 768: a computed UDP checksum of zero is transmitted as all-ones, because an on-wire
        // zero is the sentinel meaning "no checksum" and would let a corrupt datagram pass.
        putUnsignedShort(packet, IPV4_HEADER_SIZE + 6, if (udpChecksum == 0) 0xffff else udpChecksum)
        return packet
    }

    private fun udpChecksum(packet: ByteArray, udpLength: Int): Int {
        // The UDP checksum covers a pseudo-header (source and destination IPs at offsets 12 and 16,
        // the protocol number, and the UDP length) in addition to the UDP header and payload.
        var sum = 0L
        sum += unsignedShort(packet, 12)
        sum += unsignedShort(packet, 14)
        sum += unsignedShort(packet, 16)
        sum += unsignedShort(packet, 18)
        sum += UDP_PROTOCOL
        sum += udpLength
        sum += wordSum(packet, IPV4_HEADER_SIZE, udpLength)
        return finishChecksum(sum)
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int =
        finishChecksum(wordSum(bytes, offset, length))

    private fun wordSum(bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += unsignedShort(bytes, index)
            index += 2
        }
        if (index < end) sum += (bytes[index].toInt() and 0xff) shl 8
        return sum
    }

    // Fold the 32-bit accumulator into 16 bits by adding back the carries, then take the one's
    // complement — the standard Internet checksum termination (RFC 1071).
    private fun finishChecksum(initial: Long): Int {
        var sum = initial
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
