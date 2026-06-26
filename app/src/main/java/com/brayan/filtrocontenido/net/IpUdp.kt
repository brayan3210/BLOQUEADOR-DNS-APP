package com.brayan.filtrocontenido.net

/**
 * Parseo y construccion minima de paquetes IPv4 + UDP.
 *
 * El filtro fuerza el DNS por IPv4 (solo anuncia un servidor DNS IPv4), por lo
 * que basta con manejar IPv4/UDP. El tipo de pregunta DNS (A o AAAA) se resuelve
 * en la capa DNS, igual que en el PC.
 */
object IpUdp {

    private const val PROTO_UDP = 17

    class Datagram(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    )

    fun parse(packet: ByteArray, len: Int): Datagram? {
        if (len < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || ihl + 8 > len) return null
        if ((packet[9].toInt() and 0xFF) != PROTO_UDP) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val u = ihl
        val srcPort = u16(packet, u)
        val dstPort = u16(packet, u + 2)
        val udpLen = u16(packet, u + 4)

        val payloadStart = u + 8
        var payloadLen = udpLen - 8
        // Si la longitud declarada es incoherente, usar lo que llego.
        if (payloadLen < 0 || payloadStart + payloadLen > len) {
            payloadLen = len - payloadStart
        }
        if (payloadLen < 0) return null

        val payload = packet.copyOfRange(payloadStart, payloadStart + payloadLen)
        return Datagram(srcIp, dstIp, srcPort, dstPort, payload)
    }

    /**
     * Construye un paquete IPv4/UDP de respuesta. La checksum UDP se deja en 0
     * (opcional en IPv4); la checksum IPv4 si se calcula (obligatoria).
     */
    fun build(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val ipHeader = 20
        val udpLen = 8 + payload.size
        val total = ipHeader + udpLen
        val b = ByteArray(total)

        // --- IPv4 ---
        b[0] = 0x45                       // version 4, IHL 5
        b[1] = 0                          // DSCP/ECN
        b[2] = (total ushr 8).toByte()
        b[3] = total.toByte()
        // id (4,5) = 0 ; flags/fragment (6,7) = 0
        b[8] = 64                         // TTL
        b[9] = PROTO_UDP.toByte()
        // checksum (10,11) = 0 por ahora
        System.arraycopy(srcIp, 0, b, 12, 4)
        System.arraycopy(dstIp, 0, b, 16, 4)
        val csum = checksum(b, 0, ipHeader)
        b[10] = (csum ushr 8).toByte()
        b[11] = csum.toByte()

        // --- UDP ---
        val u = ipHeader
        b[u] = (srcPort ushr 8).toByte()
        b[u + 1] = srcPort.toByte()
        b[u + 2] = (dstPort ushr 8).toByte()
        b[u + 3] = dstPort.toByte()
        b[u + 4] = (udpLen ushr 8).toByte()
        b[u + 5] = udpLen.toByte()
        // checksum (u+6, u+7) = 0
        System.arraycopy(payload, 0, b, u + 8, payload.size)

        return b
    }

    private fun u16(d: ByteArray, off: Int) =
        ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)

    private fun checksum(data: ByteArray, off: Int, length: Int): Int {
        var sum = 0L
        var i = off
        var remaining = length
        while (remaining > 1) {
            sum += (((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)).toLong()
            i += 2
            remaining -= 2
        }
        if (remaining > 0) sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
