package com.brayan.filtrocontenido.net

/**
 * Parseo de la pregunta DNS y construccion de la respuesta de bloqueo.
 *
 * Respuesta de bloqueo (igual que el PC):
 *   - pregunta tipo A     -> A 0.0.0.0
 *   - pregunta tipo AAAA  -> AAAA ::
 *   - otro tipo           -> NXDOMAIN
 */
object Dns {

    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    class Question(val name: String, val qtype: Int, val questionEnd: Int)

    /** Extrae la primera pregunta del payload DNS, o null si no se puede. */
    fun parseQuestion(dns: ByteArray): Question? {
        if (dns.size < 12) return null
        val qdCount = u16(dns, 4)
        if (qdCount < 1) return null

        var pos = 12
        val sb = StringBuilder()
        while (true) {
            if (pos >= dns.size) return null
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 != 0) return null  // puntero de compresion en la pregunta: inesperado
            pos++
            if (pos + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos, len, Charsets.US_ASCII))
            pos += len
        }
        if (pos + 4 > dns.size) return null
        val qtype = u16(dns, pos)
        val questionEnd = pos + 4  // qtype(2) + qclass(2)
        return Question(sb.toString(), qtype, questionEnd)
    }

    /**
     * Construye la respuesta de bloqueo a partir de la consulta original.
     * questionEnd = indice justo despues de la seccion de pregunta.
     */
    fun buildBlocked(query: ByteArray, qtype: Int, questionEnd: Int): ByteArray {
        val answer: ByteArray? = when (qtype) {
            TYPE_A -> answerRecord(TYPE_A, byteArrayOf(0, 0, 0, 0))
            TYPE_AAAA -> answerRecord(TYPE_AAAA, ByteArray(16))
            else -> null
        }

        val size = questionEnd + (answer?.size ?: 0)
        val resp = ByteArray(size)
        System.arraycopy(query, 0, resp, 0, questionEnd)

        // Flags. byte2: QR=1, opcode copiado, AA=0, TC=0, RD copiado.
        val opcode = query[2].toInt() and 0x78
        val rd = query[2].toInt() and 0x01
        resp[2] = (0x80 or opcode or rd).toByte()
        // byte3: RA=1, RCODE = 0 (con respuesta) o 3 (NXDOMAIN).
        resp[3] = if (answer != null) 0x80.toByte() else 0x83.toByte()

        // ancount
        resp[6] = 0
        resp[7] = if (answer != null) 1 else 0
        // nscount + arcount = 0 (descartamos EDNS/OPT)
        resp[8] = 0; resp[9] = 0; resp[10] = 0; resp[11] = 0

        if (answer != null) System.arraycopy(answer, 0, resp, questionEnd, answer.size)
        return resp
    }

    private fun answerRecord(type: Int, rdata: ByteArray): ByteArray {
        val b = ByteArray(12 + rdata.size)
        b[0] = 0xC0.toByte(); b[1] = 0x0C          // puntero al nombre de la pregunta (offset 12)
        b[2] = (type ushr 8).toByte(); b[3] = type.toByte()
        b[4] = 0; b[5] = 1                          // class IN
        b[6] = 0; b[7] = 0; b[8] = 0; b[9] = 60     // TTL 60s
        b[10] = (rdata.size ushr 8).toByte(); b[11] = rdata.size.toByte()
        System.arraycopy(rdata, 0, b, 12, rdata.size)
        return b
    }

    private fun u16(d: ByteArray, off: Int) =
        ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
}
