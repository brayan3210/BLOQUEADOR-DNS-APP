package com.brayan.filtrocontenido

import android.app.Application
import com.brayan.filtrocontenido.data.UiPrefs

/**
 * Application: aplica el tema guardado (claro/oscuro) al arrancar el proceso,
 * antes de que se cree cualquier Activity, para evitar parpadeos. No toca la
 * logica del filtro; es puramente de interfaz.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UiPrefs.apply(this)
    }
}
