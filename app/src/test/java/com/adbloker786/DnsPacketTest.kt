package com.adbloker786

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {
    @Test fun parsesDnsQueryAndBuildsValidNxDomainPacket() {
        val packet = makeQueryPacket("ads.example.com")
        val request = DnsPacket.parse(packet, packet.size)
        assertNotNull(request)
        request!!
        assertEquals("ads.example.com", request.host)

        val dnsResponse = DnsPacket.errorResponse(request, 3)
        assertEquals(0x8183, u16(dnsResponse, 2))
        val response = DnsPacket.buildIpv4UdpResponse(request, dnsResponse)
        assertEquals(0x45, response[0].toInt() and 0xff)
        assertEquals(53, u16(response, 20))
        assertEquals(53000, u16(response, 22))
        assertTrue(DnsPacket.isValidIpv4HeaderChecksum(response))
        assertTrue(DnsPacket.isValidUdpChecksum(response))
    }

    private fun makeQueryPacket(host: String): ByteArray {
        val dns = ArrayList<Byte>()
        dns += 0x12.toByte()
        dns += 0x34.toByte()
        dns += 0x01.toByte()
        dns += 0x00.toByte()
        dns += 0x00.toByte()
        dns += 0x01.toByte()
        repeat(6) { dns += 0.toByte() }
        host.split('.').forEach { label ->
            dns += label.length.toByte()
            label.toByteArray(Charsets.US_ASCII).forEach { dns += it }
        }
        dns += 0.toByte()
        dns += 0.toByte()
        dns += 1.toByte()
        dns += 0.toByte()
        dns += 1.toByte()

        val dnsBytes = dns.toByteArray()
        val packet = ByteArray(20 + 8 + dnsBytes.size)
        packet[0] = 0x45
        putU16(packet, 2, packet.size)
        putU16(packet, 4, 0x2222)
        packet[8] = 64
        packet[9] = 17
        byteArrayOf(192.toByte(), 0, 2, 10).copyInto(packet, 12)
        byteArrayOf(10, 77, 0, 2).copyInto(packet, 16)
        putU16(packet, 20, 53000)
        putU16(packet, 22, 53)
        putU16(packet, 24, 8 + dnsBytes.size)
        dnsBytes.copyInto(packet, 28)
        return packet
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
