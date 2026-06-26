package com.brayan.filtrocontenido.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Administrador de dispositivo. Mientras este activo, Android obliga a
 * desactivarlo ANTES de poder desinstalar la app. El guardian de accesibilidad
 * vigila justamente esa pantalla de desactivacion.
 */
class FilterDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Si desactivas el administrador, el Filtro de Contenido dejara de protegerte. " +
            "Recuerda por que lo instalaste: estas decidiendo en frio, no en un impulso."
    }

    companion object {
        fun component(context: Context) =
            ComponentName(context, FilterDeviceAdminReceiver::class.java)
    }
}
