package com.brayan.filtrocontenido.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Refuerzo de capa 3 (solo si la app es Device Owner, activado por ADB).
 * Cuando lo es:
 *   - bloquea la desinstalacion (la opcion ni aparece),
 *   - desactiva el DNS privado (DoT) del sistema, que se saltaria la VPN,
 *   - fuerza la VPN always-on con lockdown (sin red si la VPN no esta activa).
 *
 * Si la app NO es Device Owner, todos los metodos no hacen nada.
 */
object DeviceOwnerManager {

    fun isOwner(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(ctx.packageName)
    }

    fun applyHardening(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val admin = FilterDeviceAdminReceiver.component(ctx)

        try { dpm.setUninstallBlocked(admin, ctx.packageName, true) } catch (_: Exception) {}

        // DNS privado (DoT) -> off, para que no evada la VPN.
        try {
            dpm.setGlobalSetting(admin, "private_dns_mode", "off")
        } catch (_: Exception) {}

        // VPN always-on con lockdown: sin VPN no hay red.
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, true)
            } else {
                dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, false)
            }
        } catch (_: Exception) {}
    }
}
