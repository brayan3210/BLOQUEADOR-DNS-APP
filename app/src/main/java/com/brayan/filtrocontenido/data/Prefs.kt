package com.brayan.filtrocontenido.data

import android.content.Context

/**
 * Acceso centralizado a la configuracion persistente (SharedPreferences).
 * Equivale al config.json del filtro de PC.
 */
object Prefs {

    private const val FILE = "filtro_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_PWD_HASH = "password_hash"
    private const val KEY_UNLOCK_UNTIL = "unlock_until"
    private const val KEY_LISTS_UPDATED = "lists_updated_at"
    private const val KEY_UPSTREAM1 = "upstream1"
    private const val KEY_UPSTREAM2 = "upstream2"
    private const val KEY_BLOCKED_COUNT = "blocked_count"

    const val DEFAULT_UPSTREAM1 = "1.1.1.1"
    const val DEFAULT_UPSTREAM2 = "1.0.0.1"

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = p(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    fun passwordHash(ctx: Context): String? = p(ctx).getString(KEY_PWD_HASH, null)
    fun setPasswordHash(ctx: Context, hash: String) =
        p(ctx).edit().putString(KEY_PWD_HASH, hash).apply()

    fun hasPassword(ctx: Context) = !passwordHash(ctx).isNullOrEmpty()

    /** Ventana temporal (ms epoch) durante la cual el guardian permite tocar ajustes. */
    fun unlockUntil(ctx: Context) = p(ctx).getLong(KEY_UNLOCK_UNTIL, 0L)
    fun openUnlockWindow(ctx: Context, durationMs: Long) =
        p(ctx).edit().putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + durationMs).apply()
    fun closeUnlockWindow(ctx: Context) = p(ctx).edit().putLong(KEY_UNLOCK_UNTIL, 0L).apply()
    fun isUnlocked(ctx: Context) = System.currentTimeMillis() < unlockUntil(ctx)

    fun upstream1(ctx: Context): String = p(ctx).getString(KEY_UPSTREAM1, DEFAULT_UPSTREAM1)!!
    fun upstream2(ctx: Context): String = p(ctx).getString(KEY_UPSTREAM2, DEFAULT_UPSTREAM2)!!

    fun listsUpdatedAt(ctx: Context) = p(ctx).getLong(KEY_LISTS_UPDATED, 0L)
    fun setListsUpdatedAt(ctx: Context, t: Long) =
        p(ctx).edit().putLong(KEY_LISTS_UPDATED, t).apply()

    fun blockedCount(ctx: Context) = p(ctx).getLong(KEY_BLOCKED_COUNT, 0L)
    fun incBlocked(ctx: Context) {
        val pref = p(ctx)
        pref.edit().putLong(KEY_BLOCKED_COUNT, pref.getLong(KEY_BLOCKED_COUNT, 0L) + 1).apply()
    }
}
