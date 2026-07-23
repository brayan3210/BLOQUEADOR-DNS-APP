package com.brayan.filtrocontenido.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Preferencias SOLO de interfaz (tema claro/oscuro). Vive aparte de [Prefs] para
 * no tocar la logica del filtro: cambiar el tema no afecta al bloqueo.
 */
object UiPrefs {

    private const val FILE = "ui_prefs"
    private const val KEY_DARK = "dark_mode"

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Por defecto oscuro: es el diseno de referencia. */
    fun isDark(ctx: Context): Boolean = p(ctx).getBoolean(KEY_DARK, true)

    fun setDark(ctx: Context, dark: Boolean) =
        p(ctx).edit().putBoolean(KEY_DARK, dark).apply()

    /** Aplica el modo guardado al proceso (llamar antes de inflar la vista). */
    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(ctx)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
