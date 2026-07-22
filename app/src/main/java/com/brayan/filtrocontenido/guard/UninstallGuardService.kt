package com.brayan.filtrocontenido.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brayan.filtrocontenido.BloqueoBusquedaActivity
import com.brayan.filtrocontenido.MainActivity
import com.brayan.filtrocontenido.data.Prefs
import com.brayan.filtrocontenido.filter.SearchTermFilter

/**
 * Servicio de accesibilidad con DOS trabajos:
 *
 *  1. Guardian de desinstalacion (capa 2): saca al usuario de las pantallas de
 *     desinstalar / forzar / desactivar admin o accesibilidad de ESTA app,
 *     salvo que haya una ventana de desbloqueo por contrasena.
 *
 *  2. Bloqueo de BUSQUEDAS (nuevo): en navegadores, lee el texto de la barra de
 *     busqueda / URL y, si contiene un termino explicito del catalogo (mismo del
 *     PC, con excepciones educativas), tapa la pantalla con un aviso. No usa
 *     SafeSearch; lee el texto en pantalla, no descifra trafico.
 *
 * Limite honesto: Modo Seguro, restablecimiento de fabrica y ADB siguen siendo
 * vias de escape. Y solo cubre lo que se rinde como texto de accesibilidad.
 */
class UninstallGuardService : AccessibilityService() {

    private val appLabel = "filtro de contenido"

    private val monitoredPackages = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.settings.intelligence",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.packageinstaller",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.huawei.systemmanager",
        "com.vivo.packageinstaller",
        "com.oneplus.security"
    )

    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser",              // Samsung Internet
        "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.brave.browser",
        "com.microsoft.emmx",                        // Edge
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.duckduckgo.mobile.android",
        "com.google.android.googlequicksearchbox",   // app de Google
        "com.android.browser",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.UCMobile.intl"
    )

    private val sensitiveWords = listOf(
        "desinstalar", "uninstall",
        "forzar detencion", "forzar detención", "force stop", "detener",
        "desactivar", "deactivate", "disable",
        "administrador de dispositivo", "administradores", "device admin", "device administrator",
        "accesibilidad", "accessibility",
        "quitar", "remove", "eliminar"
    )

    @Volatile private var lastCheckAt = 0L
    @Volatile private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        try { if (!SearchTermFilter.isLoaded()) SearchTermFilter.load(this) } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val type = event.eventType

        // ---- Trabajo 2: bloqueo de busquedas (siempre activo) ----
        if (pkg in browserPackages) {
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            ) checkSearch()
            return
        }

        // ---- Trabajo 1: guardian de desinstalacion ----
        if (Prefs.isUnlocked(this)) return  // el usuario se autentico
        if (pkg !in monitoredPackages) return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(64)
        collectText(root, texts, 0)
        try { root.recycle() } catch (_: Exception) {}

        val joined = texts.joinToString(" \n ").lowercase()
        val mentionsApp = joined.contains(appLabel)
        val installerScreen = pkg.contains("packageinstaller")
        val sensitive = sensitiveWords.any { joined.contains(it) }

        if ((installerScreen && mentionsApp) || (mentionsApp && sensitive)) {
            blockAndWarn()
        }
    }

    // ---------------------------------------------------------------- busquedas

    private fun checkSearch() {
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < 350) return          // throttle (no en cada evento)
        lastCheckAt = now
        if (now - lastBlockAt < 2500) return          // ya bloqueamos hace poco

        val root = rootInActiveWindow ?: return
        val cands = ArrayList<String>(16)
        collectSearchCandidates(root, cands, 0)
        try { root.recycle() } catch (_: Exception) {}

        for (c in cands) {
            if (SearchTermFilter.blockReason(c) != null) {
                lastBlockAt = now
                blockSearch()
                return
            }
        }
    }

    private fun collectSearchCandidates(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 40) return
        val id = node.viewIdResourceName ?: ""
        val isUrlBar = id.contains("url", true) || id.contains("omnibox", true) ||
            id.contains("location", true) || id.contains("search", true)
        if (node.isEditable || isUrlBar) {
            node.text?.let { if (it.isNotEmpty()) out.add(it.toString()) }
            if (isUrlBar) node.contentDescription?.let { if (it.isNotEmpty()) out.add(it.toString()) }
        }
        for (i in 0 until node.childCount) collectSearchCandidates(node.getChild(i), out, depth + 1)
    }

    private fun blockSearch() {
        try { Prefs.incBlocked(this) } catch (_: Exception) {}
        performGlobalAction(GLOBAL_ACTION_BACK)
        try {
            startActivity(Intent(this, BloqueoBusquedaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- guardian

    private fun blockAndWarn() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            val i = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(MainActivity.EXTRA_GUARD_BLOCK, true)
            }
            startActivity(i)
        } catch (_: Exception) { }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 400) return
        node.text?.let { if (it.isNotEmpty()) out.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotEmpty()) out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depth + 1)
        }
    }

    override fun onInterrupt() {}
}
