package com.brayan.filtrocontenido.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brayan.filtrocontenido.MainActivity
import com.brayan.filtrocontenido.data.Prefs

/**
 * Guardian de desinstalacion (capa 2, la que de verdad frena el impulso).
 *
 * Vigila cuando el usuario entra a las pantallas peligrosas para ESTA app
 * (desinstalar, forzar detencion, informacion de la app, desactivar el
 * administrador de dispositivo, o desactivar este mismo servicio de
 * accesibilidad) y, salvo que haya una ventana de desbloqueo abierta por
 * contrasena, lo saca de ahi (atras + inicio) y abre la app con un aviso.
 *
 * Limite honesto: Modo Seguro, restablecimiento de fabrica y ADB siguen siendo
 * vias de escape. Para cerrarlas se necesita Device Owner (ver README).
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

    private val sensitiveWords = listOf(
        "desinstalar", "uninstall",
        "forzar detencion", "forzar detención", "force stop", "detener",
        "desactivar", "deactivate", "disable",
        "administrador de dispositivo", "administradores", "device admin", "device administrator",
        "accesibilidad", "accessibility",
        "quitar", "remove", "eliminar"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (Prefs.isUnlocked(this)) return  // el usuario se autentico: dejar pasar

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in monitoredPackages) return

        val type = event.eventType
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

        // Bloquear si: dialogo del instalador sobre esta app, o pantalla de
        // ajustes que menciona la app junto a una accion peligrosa.
        if ((installerScreen && mentionsApp) || (mentionsApp && sensitive)) {
            blockAndWarn()
        }
    }

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
