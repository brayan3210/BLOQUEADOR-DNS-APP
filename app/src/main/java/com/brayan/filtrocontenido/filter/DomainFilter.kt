package com.brayan.filtrocontenido.filter

/**
 * Decision de bloqueo. Misma logica que filtro.py del PC:
 *   1) coincidencia exacta o de dominio padre contra la lista de dominios,
 *   2) palabra clave contenida dentro del nombre de dominio.
 *
 * Las referencias a las colecciones son @Volatile y se reemplazan enteras al
 * recargar, de modo que la lectura desde los hilos de la VPN es segura sin locks.
 */
object DomainFilter {

    @Volatile private var domains: Set<String> = emptySet()
    @Volatile private var keywords: List<String> = emptyList()

    fun update(newDomains: Set<String>, newKeywords: List<String>) {
        domains = newDomains
        keywords = newKeywords
    }

    fun domainCount() = domains.size
    fun keywordCount() = keywords.size
    fun isLoaded() = domains.isNotEmpty() || keywords.isNotEmpty()

    /** Devuelve el motivo del bloqueo, o null si se permite. */
    fun blockReason(host: String): String? {
        val q = host.lowercase().trim('.')
        if (q.isEmpty()) return null

        // 1) dominio exacto o cualquier dominio padre (sub.ejemplo.com -> ejemplo.com).
        val labels = q.split('.')
        val d = domains
        for (i in labels.indices) {
            val candidate = if (i == 0) q else labels.subList(i, labels.size).joinToString(".")
            if (d.contains(candidate)) return "lista"
        }

        // 2) palabra clave como subcadena del dominio.
        for (kw in keywords) {
            if (q.contains(kw)) return "palabra:$kw"
        }
        return null
    }
}
