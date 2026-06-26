package com.brayan.filtrocontenido.lock

import android.content.Context
import com.brayan.filtrocontenido.data.Prefs
import java.security.MessageDigest

/**
 * Candado por contrasena (igual que el PC): solo se guarda el hash SHA-256;
 * el texto plano nunca se almacena.
 *
 * Consejo de autocontrol (documentado): que la contrasena la escriba una
 * persona de confianza y NO te la diga. Asi no podras desactivarlo en un
 * momento de debilidad.
 */
object PasswordManager {

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPassword(ctx: Context, plain: String) {
        Prefs.setPasswordHash(ctx, sha256(plain))
    }

    fun isSet(ctx: Context) = Prefs.hasPassword(ctx)

    fun verify(ctx: Context, plain: String): Boolean {
        val stored = Prefs.passwordHash(ctx) ?: return false
        return stored.equals(sha256(plain), ignoreCase = true)
    }
}
