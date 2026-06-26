package com.brayan.filtrocontenido.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.brayan.filtrocontenido.data.Prefs
import com.brayan.filtrocontenido.vpn.DnsVpnService

/**
 * Re-arranca el filtro tras reiniciar el telefono o actualizar la app.
 * Equivale a la tarea programada AtStartup/AtLogon del PC.
 *
 * Funciona porque el permiso de VPN ya fue concedido una vez: tras la primera
 * autorizacion, VpnService.prepare() devuelve null y establish() no pide UI.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!relevant) return

        if (Prefs.isEnabled(context)) {
            val i = Intent(context, DnsVpnService::class.java)
                .setAction(DnsVpnService.ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
