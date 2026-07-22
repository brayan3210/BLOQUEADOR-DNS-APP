package com.brayan.filtrocontenido.filter

import android.content.Context
import java.text.Normalizer

/**
 * Filtro de BUSQUEDAS por palabra (equivale a motor_busqueda.py del PC).
 *
 * A diferencia de DomainFilter (que mira DOMINIOS), esto evalua el TEXTO que el
 * usuario escribe en el buscador o barra de direcciones, leido por el servicio
 * de accesibilidad. No usa SafeSearch.
 *
 * Reglas (iguales al PC):
 *   - Bloquea si el texto contiene un termino EXPLICITO del catalogo.
 *   - Palabras anatomicas/educativas (pene, vagina, menstruacion...) NO bloquean
 *     solas: se restan del catalogo. Solo bloquean si coocurre un explicito.
 *   - Termino de una palabra: coincide por inicio de palabra (\bporn atrapa
 *     porn/porno pero NO essex). Termino con espacios: subcadena.
 */
object SearchTermFilter {

    // Archivos de terminos a bloquear (en assets/).
    private val CATALOG_ASSETS = listOf("catalogo_busqueda.txt", "actrices_porno.txt")
    private const val EXCEPTIONS_ASSET = "excepciones_educativas.txt"

    @Volatile private var multiword: List<String> = emptyList()
    @Volatile private var single: List<String> = emptyList()
    @Volatile private var singleRegex: Regex? = null

    fun isLoaded() = multiword.isNotEmpty() || single.isNotEmpty()
    fun termCount() = multiword.size + single.size

    /** Carga las listas desde assets. Idempotente. */
    fun load(ctx: Context) {
        val catalog = ArrayList<String>()
        for (name in CATALOG_ASSETS) catalog += readAsset(ctx, name)
        val exceptions = readAsset(ctx, EXCEPTIONS_ASSET).toHashSet()

        val seen = HashSet<String>()
        val terms = ArrayList<String>()
        for (t in catalog) {
            if (t in exceptions || t in seen) continue   // excepcion nunca bloquea sola
            seen.add(t)
            terms.add(t)
        }
        val mw = terms.filter { it.contains(' ') }
        val sw = terms.filter { !it.contains(' ') }
        multiword = mw
        single = sw
        singleRegex = if (sw.isEmpty()) null
        else Regex("\\b(" + sw.joinToString("|") { Regex.escape(it) } + ")")
    }

    /** Devuelve el termino que bloquea, o null si se permite. */
    fun blockReason(text: String?): String? {
        val q = normalize(text ?: return null)
        if (q.isEmpty()) return null
        for (t in multiword) if (q.contains(t)) return t
        singleRegex?.find(q)?.let { return it.groupValues[1] }
        return null
    }

    // --- helpers ---

    private fun normalize(input: String): String {
        // decodificar %20 etc. de las URLs -> espacio
        var s = input.replace(Regex("%[0-9a-fA-F]{2}"), " ").lowercase()
        // quitar acentos
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        // dejar solo [a-z0-9 espacio]
        s = s.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return s
    }

    private fun readAsset(ctx: Context, name: String): List<String> {
        return try {
            ctx.assets.open(name).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { normalize(it) }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
