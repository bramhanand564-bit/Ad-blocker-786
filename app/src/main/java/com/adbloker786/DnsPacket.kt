package com.adbloker786

import java.net.IDN
import java.util.Locale

internal object DnsPacket {
    data class Request(
        val dnsMessage: ByteArray,
        val host: String,
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val identification: Int,
        val questionEnd: Int
    )

    fun parse(packet: ByteArray, size: Int): Request? {
        if (size < 28 || (packet[0].toInt() ushr 4 and 0x0f) != 4) return null
        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        if (ipHeaderLength < 20 || size < ipHeaderLength + 8) return null
        val totalLength = u16(packet, 2)
        if (totalLength < ipHeaderLength + 8 || totalLength > size) return null
        if ((packet[9].toInt() and 0xff) != 17) return null
        if ((u16(packet, 6) and 0x3fff) != 0) return null

        val udpOffset = ipHeaderLength
        if (u16(packet, udpOffset + 2) != 53) return null
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 25 || udpOffset + udpLength > totalLength) return null

        val dns = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        if ((dns[2].toInt() and 0x80) != 0 || u16(dns, 4) != 1) return null
        val question = readQuestion(dns) ?: return null
        return Request(
            dnsMessage = dns,
            host = question.first,
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = u16(packet, udpOffset),
            identification = u16(packet, 4),
            questionEnd = question.second
        )
    }

    private fun readQuestion(dns: ByteArray): Pair<String, Int>? {
        var offset = 12
        val labels = ArrayList<String>()
        var totalLabelLength = 0
        while (offset < dns.size) {
            val length = dns[offset++].toInt() and 0xff
            if (length == 0) break
            if (length > 63 || (length and 0xc0) != 0 || offset + length > dns.size) return null
            totalLabelLength += length + 1
            if (totalLabelLength > 255) return null
            labels += String(dns, offset, length, Charsets.US_ASCII)
            offset += length
        }
        if (labels.isEmpty() || offset + 4 > dns.size) return null
        val host = try {
            IDN.toASCII(labels.joinToString("."), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return host to (offset + 4)
    }

    fun errorResponse(request: Request, rcode: Int): ByteArray {
        val response = request.dnsMessage.copyOf(request.questionEnd)
        val queryFlags = u16(request.dnsMessage, 2)
        val responseFlags = 0x8000 or (queryFlags and 0x7910) or 0x0080 or (rcode and 0x0f)
        putU16(response, 2, responseFlags)
        putU16(response, 4, 1)
        putU16(response, 6, 0)
        putU16(response, 8, 0)
        putU16(response, 10, 0)
        return response
    }

    fun buildIpv4UdpResponse(request: Request, dnsMessage: ByteArray): ByteArray {
        require(dnsMessage.size <= 1400) { "DNS response is too large for the VPN MTU" }
        val udpLength = 8 + dnsMessage.size
        val packet = ByteArray(20 + udpLength)
        packet[0] = 0x45
        putU16(packet, 2, packet.size)
        putU16(packet, 4, request.identification)
        putU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 17
        request.destinationAddress.copyInto(packet, 12)
        request.sourceAddress.copyInto(packet, 16)
        putU16(packet, 10, checksum(packet, 0, 20))

        putU16(packet, 20, 53)
        putU16(packet, 22, request.sourcePort)
        putU16(packet, 24, udpLength)
        dnsMessage.copyInto(packet, 28)

        var pseudoSum = 0L
        pseudoSum += sumWords(request.destinationAddress, 0, 4)
        pseudoSum += sumWords(request.sourceAddress, 0, 4)
        pseudoSum += 17
        pseudoSum += udpLength
        val udpChecksum = checksum(packet, 20, udpLength, pseudoSum).let { if (it == 0) 0xffff else it }
        putU16(packet, 26, udpChecksum)
        return packet
    }

    internal fun isValidIpv4HeaderChecksum(packet: ByteArray): Boolean =
        packet.size >= 20 && checksum(packet, 0, 20) == 0

    internal fun isValidUdpChecksum(packet: ByteArray): Boolean {
        if (packet.size < 28) return false
        val udpLength = u16(packet, 24)
        if (udpLength < 8 || 20 + udpLength > packet.size) return false
        var pseudoSum = 0L
        pseudoSum += sumWords(packet, 12, 8)
        pseudoSum += 17
        pseudoSum += udpLength
        return checksum(packet, 20, udpLength, pseudoSum) == 0
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun sumWords(bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ((bytes[index].toInt() and 0xff) shl 8) or (bytes[index + 1].toInt() and 0xff)
            index += 2
        }
        if (index < end) sum += (bytes[index].toInt() and 0xff) shl 8
        return sum
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int, seed: Long = 0L): Int {
        var sum = seed + sumWords(bytes, offset, length)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffffL) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }
}
