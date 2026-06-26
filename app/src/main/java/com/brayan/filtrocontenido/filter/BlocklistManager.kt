package com.brayan.filtrocontenido.filter

import android.content.Context
import com.brayan.filtrocontenido.data.Prefs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Carga y actualiza las listas de bloqueo.
 *
 *  - Empaquetadas en assets/ (siempre disponibles, bloquean lo principal sin internet):
 *      dominios_personalizados.txt, doh_bypass.txt, palabras_clave.txt
 *  - Descargadas en filesDir/listas (las grandes, ~260k dominios):
 *      StevenBlack porn + Hagezi nsfw   (mismas fuentes que el PC)
 *
 * Soporta formato hosts ("0.0.0.0 dominio"), dominio plano y sintaxis Adblock
 * ("||dominio^"), de modo que la lista de Hagezi se parsea correctamente.
 */
object BlocklistManager {

    private const val DIR = "listas"

    private val ASSET_DOMAIN_FILES = listOf("dominios_personalizados.txt", "doh_bypass.txt")
    private const val ASSET_KEYWORDS = "palabras_clave.txt"

    val SOURCES = linkedMapOf(
        "lista_porno_stevenblack.txt" to
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
        "lista_porno_hagezi.txt" to
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/nsfw.txt"
    )

    private val HOSTS_PREFIXES = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
    private val IGNORE_HOSTNAMES = setOf("localhost", "localhost.localdomain", "broadcasthost")
    private val DOMAIN_REGEX = Regex("[a-z0-9._-]+")

    fun listsDir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun downloadedFilesExist(ctx: Context): Boolean {
        val dir = listsDir(ctx)
        return SOURCES.keys.any { File(dir, it).length() > 0 }
    }

    /** Carga todo a memoria y actualiza el DomainFilter. */
    fun load(ctx: Context) {
        val domains = HashSet<String>(300_000)
        val keywords = ArrayList<String>()

        for (name in ASSET_DOMAIN_FILES) parseAssetDomains(ctx, name, domains)
        parseAssetKeywords(ctx, ASSET_KEYWORDS, keywords)

        val dir = listsDir(ctx)
        for (name in SOURCES.keys) {
            val f = File(dir, name)
            if (f.exists() && f.length() > 0) parseFileDomains(f, domains)
        }

        DomainFilter.update(domains, keywords)
    }

    /** Descarga las listas grandes y recarga. Debe llamarse en un hilo de fondo. */
    fun download(ctx: Context): Pair<Boolean, String> {
        val dir = listsDir(ctx)
        var ok = 0
        val msgs = StringBuilder()
        for ((name, url) in SOURCES) {
            try {
                val bytes = httpGet(url)
                File(dir, name).writeBytes(bytes)
                ok++
                msgs.append("• $name OK\n")
            } catch (e: Exception) {
                msgs.append("• $name ERROR: ${e.message}\n")
            }
        }
        if (ok > 0) Prefs.setListsUpdatedAt(ctx, System.currentTimeMillis())
        load(ctx)
        return Pair(ok == SOURCES.size, msgs.toString().trim())
    }

    private fun httpGet(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FiltroContenido-Android")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
    }

    // ---- parsing ----

    private fun parseAssetDomains(ctx: Context, asset: String, out: MutableSet<String>) {
        try {
            ctx.assets.open(asset).use { ins ->
                BufferedReader(InputStreamReader(ins)).forEachLine { line ->
                    parseDomainLine(line)?.let { out.add(it) }
                }
            }
        } catch (_: Exception) { /* asset opcional */ }
    }

    private fun parseAssetKeywords(ctx: Context, asset: String, out: MutableList<String>) {
        try {
            ctx.assets.open(asset).use { ins ->
                BufferedReader(InputStreamReader(ins)).forEachLine { raw ->
                    val l = raw.trim().lowercase()
                    if (l.isNotEmpty() && !l.startsWith("#")) out.add(l)
                }
            }
        } catch (_: Exception) { }
    }

    private fun parseFileDomains(file: File, out: MutableSet<String>) {
        file.bufferedReader().useLines { lines ->
            lines.forEach { line -> parseDomainLine(line)?.let { out.add(it) } }
        }
    }

    /** Extrae un dominio limpio de una linea en formato hosts / plano / Adblock. */
    private fun parseDomainLine(line: String): String? {
        val l = line.trim()
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("!")) return null

        val parts = l.split(Regex("\\s+"))
        var token = if (parts.size >= 2 && parts[0] in HOSTS_PREFIXES) parts[1] else parts[0]

        // Sintaxis Adblock: ||dominio^...
        if (token.startsWith("||")) token = token.substring(2)
        token = token.trimStart('|')
        val caret = token.indexOf('^')
        if (caret >= 0) token = token.substring(0, caret)

        // Descartar comodines/regex/rutas que no son dominios simples.
        if (token.contains('*') || token.contains('/') || token.contains('$')) return null

        val d = token.lowercase().trim('.')
        if (d.isEmpty() || d in IGNORE_HOSTNAMES) return null
        if (!d.contains('.')) return null
        if (!DOMAIN_REGEX.matches(d)) return null
        return d
    }
}
