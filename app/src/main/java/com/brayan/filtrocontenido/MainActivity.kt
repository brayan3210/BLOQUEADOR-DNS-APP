package com.brayan.filtrocontenido

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.brayan.filtrocontenido.admin.DeviceOwnerManager
import com.brayan.filtrocontenido.admin.FilterDeviceAdminReceiver
import com.brayan.filtrocontenido.data.Prefs
import com.brayan.filtrocontenido.data.UiPrefs
import com.brayan.filtrocontenido.databinding.ActivityMainBinding
import com.brayan.filtrocontenido.filter.BlocklistManager
import com.brayan.filtrocontenido.filter.DomainFilter
import com.brayan.filtrocontenido.filter.SearchTermFilter
import com.brayan.filtrocontenido.lock.PasswordManager
import com.brayan.filtrocontenido.vpn.DnsVpnService
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Date

/**
 * Pantalla principal. Esta clase solo cambio su capa de PRESENTACION en el
 * rediseno: toda la logica del filtro (VPN/DNS, contrasena, administrador de
 * dispositivo, guardian de accesibilidad y actualizacion de listas) es la misma
 * de antes. Lo nuevo es la navegacion por pestanas, el centro de notificaciones,
 * el cambio de tema claro/oscuro y los enlaces (donacion / GitHub).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GUARD_BLOCK = "guard_block"
        private const val UNLOCK_WINDOW_MS = 5 * 60 * 1000L
        private const val URL_DONATE =
            "https://www.paypal.com/donate/?hosted_button_id=ANE8JAX7MG5FE"
        private const val URL_GITHUB = "https://github.com/brayan3210"
        private const val URL_OWNER_DOC =
            "https://github.com/brayan3210/BLOQUEADOR-DNS-APP/blob/main/device-owner/INSTRUCCIONES.md"
        private const val STATE_TAB = "state_tab"
    }

    private lateinit var binding: ActivityMainBinding

    // Pestana visible actualmente. Se conserva al recrear la Activity (p.ej. al
    // cambiar de tema) para no volver siempre a Inicio.
    private var currentTab: Int = R.id.nav_inicio

    private val vpnPrepare =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startFilter()
            else toast(getString(R.string.vpn_denied))
            refresh()
        }

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fundido de entrada (acompana el cambio de tema).
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(240).start()

        // Cargar listas (dominios + terminos de busqueda) en segundo plano.
        Thread {
            try { if (!DomainFilter.isLoaded()) BlocklistManager.load(this) } catch (_: Exception) {}
            try { if (!SearchTermFilter.isLoaded()) SearchTermFilter.load(this) } catch (_: Exception) {}
            runOnUiThread { refresh() }
        }.start()

        if (Build.VERSION.SDK_INT >= 33) {
            requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Restaurar la pestana que estaba abierta antes de recrear (cambio de tema, etc.).
        currentTab = savedInstanceState?.getInt(STATE_TAB, R.id.nav_inicio) ?: R.id.nav_inicio

        wireUi()

        if (intent?.getBooleanExtra(EXTRA_GUARD_BLOCK, false) == true) {
            AlertDialog.Builder(this)
                .setTitle(R.string.guard_blocked_title)
                .setMessage(R.string.guard_blocked_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Si la app es Device Owner (capa 3), reaplicar el blindaje.
        try { DeviceOwnerManager.applyHardening(this) } catch (_: Exception) {}
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Recordar la pestana abierta para restaurarla al recrear (cambio de tema).
        outState.putInt(STATE_TAB, currentTab)
    }

    // ================================================================ UI wiring

    private fun wireUi() {
        // Acciones de proteccion / refuerzo (misma logica; ahora en varias pantallas).
        binding.btnToggle.setOnClickListener { onToggle() }
        binding.btnPassword.setOnClickListener { onSetPassword() }
        binding.btnDeviceAdmin.setOnClickListener { onDeviceAdmin() }
        binding.btnAccessibility.setOnClickListener { onAccessibility() }
        binding.btnDeviceOwner.setOnClickListener { onDeviceOwner() }
        binding.btnUpdateLists.setOnClickListener { onUpdateLists() }
        binding.btnUpdateListsFull.setOnClickListener { onUpdateLists() }

        binding.rowPassword2.setOnClickListener { onSetPassword() }
        binding.rowAdmin2.setOnClickListener { onDeviceAdmin() }
        binding.rowGuard2.setOnClickListener { onAccessibility() }
        binding.rowOwner2.setOnClickListener { onDeviceOwner() }
        binding.rowNotifPerms.setOnClickListener { openNotificationSettings() }

        // Cabecera.
        binding.btnBell.setOnClickListener { showNotifications() }
        binding.btnStatusHeader.setOnClickListener { selectPage(R.id.nav_inicio) }
        binding.btnMenu.setOnClickListener { showMenu(it) }

        // Enlaces.
        binding.btnDonate.setOnClickListener { openUrl(URL_DONATE) }
        binding.rowGithub.setOnClickListener { openUrl(URL_GITHUB) }

        // Cambio de tema (fila completa clicable; el switch es solo indicador).
        (binding.switchTheme.parent as View).setOnClickListener { toggleTheme() }

        // Navegacion inferior.
        binding.bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId); true
        }
        binding.bottomNav.selectedItemId = currentTab
        showPage(currentTab)
    }

    private fun selectPage(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun showPage(itemId: Int) {
        currentTab = itemId
        binding.pageInicio.visibility = View.GONE
        binding.pageActividad.visibility = View.GONE
        binding.pageListas.visibility = View.GONE
        binding.pageAjustes.visibility = View.GONE
        binding.pageMas.visibility = View.GONE
        val page: View = when (itemId) {
            R.id.nav_actividad -> binding.pageActividad
            R.id.nav_listas -> binding.pageListas
            R.id.nav_ajustes -> binding.pageAjustes
            R.id.nav_mas -> binding.pageMas
            else -> binding.pageInicio
        }
        page.visibility = View.VISIBLE
        page.scrollTo(0, 0)
        // Micro-animacion de entrada de pagina.
        page.alpha = 0.6f
        page.animate().alpha(1f).setDuration(180).start()
    }

    // ================================================================ tema

    private fun toggleTheme() {
        val toDark = !UiPrefs.isDark(this)
        UiPrefs.setDark(this, toDark)
        binding.root.animate().alpha(0f).setDuration(160).withEndAction {
            AppCompatDelegate.setDefaultNightMode(
                if (toDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }.start()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (UiPrefs.isDark(this@MainActivity)) "Tema claro" else "Tema oscuro")
            menu.add(0, 2, 1, "Donar (PayPal)")
            menu.add(0, 3, 2, "GitHub del proyecto")
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> { toggleTheme(); true }
                    2 -> { openUrl(URL_DONATE); true }
                    3 -> { openUrl(URL_GITHUB); true }
                    else -> false
                }
            }
            show()
        }
    }

    // ================================================================ toggle

    private fun onToggle() {
        if (DnsVpnService.isRunning) {
            // Desactivar requiere contrasena (friccion).
            if (!PasswordManager.isSet(this)) {
                confirmStopWithoutPassword()
                return
            }
            promptPassword(getString(R.string.pwd_to_disable)) { ok ->
                if (ok) {
                    Prefs.openUnlockWindow(this, UNLOCK_WINDOW_MS)
                    Prefs.setEnabled(this, false)
                    stopFilter()
                    removeDeviceAdmin()
                    toast(getString(R.string.disabled_window))
                } else {
                    toast(getString(R.string.pwd_wrong))
                }
                refresh()
            }
        } else {
            // Activar.
            if (!PasswordManager.isSet(this)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.set_pwd_first_title)
                    .setMessage(R.string.set_pwd_first_msg)
                    .setPositiveButton(R.string.set_pwd_now) { _, _ -> onSetPassword() }
                    .setNegativeButton(R.string.activate_anyway) { _, _ -> prepareVpn() }
                    .show()
            } else {
                prepareVpn()
            }
        }
    }

    private fun confirmStopWithoutPassword() {
        AlertDialog.Builder(this)
            .setTitle(R.string.no_pwd_title)
            .setMessage(R.string.no_pwd_msg)
            .setPositiveButton(R.string.disable) { _, _ ->
                Prefs.setEnabled(this, false)
                stopFilter()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPrepare.launch(intent)
        else startFilter()
    }

    private fun startFilter() {
        val i = Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, i)
        toast(getString(R.string.activated))
        suggestHardening()
        refresh()
    }

    private fun stopFilter() {
        val i = Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP)
        ContextCompat.startForegroundService(this, i)
    }

    private fun suggestHardening() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminOn = dpm.isAdminActive(FilterDeviceAdminReceiver.component(this))
        val accOn = isAccessibilityEnabled()
        if (!adminOn || !accOn) {
            AlertDialog.Builder(this)
                .setTitle(R.string.harden_title)
                .setMessage(R.string.harden_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // ================================================================ password

    private fun onSetPassword() {
        if (PasswordManager.isSet(this)) {
            promptPassword(getString(R.string.pwd_current)) { ok ->
                if (ok) askNewPassword() else toast(getString(R.string.pwd_wrong))
            }
        } else {
            askNewPassword()
        }
    }

    private fun askNewPassword() {
        val input = passwordField()
        AlertDialog.Builder(this)
            .setTitle(R.string.pwd_new_title)
            .setMessage(R.string.pwd_new_msg)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val p = input.text.toString()
                if (p.length < 4) {
                    toast(getString(R.string.pwd_too_short))
                } else {
                    PasswordManager.setPassword(this, p)
                    toast(getString(R.string.pwd_saved))
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptPassword(title: String, onResult: (Boolean) -> Unit) {
        val input = passwordField()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onResult(PasswordManager.verify(this, input.text.toString()))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onResult(false) }
            .show()
    }

    private fun passwordField(): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(48, 32, 48, 32)
    }

    // ================================================================ admin / accessibility

    private fun onDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = FilterDeviceAdminReceiver.component(this)
        if (dpm.isAdminActive(component)) {
            toast(getString(R.string.admin_already))
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain))
        }
        startActivity(intent)
    }

    private fun removeDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = FilterDeviceAdminReceiver.component(this)
        if (dpm.isAdminActive(component)) {
            try { dpm.removeActiveAdmin(component) } catch (_: Exception) {}
        }
    }

    private fun onAccessibility() {
        AlertDialog.Builder(this)
            .setTitle(R.string.acc_title)
            .setMessage(R.string.acc_msg)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.contains("$packageName/")
    }

    /**
     * Capa 3 (Device Owner). Android NO permite que una app se convierta en Device
     * Owner por si misma: se hace una sola vez por ADB desde el PC. Por eso:
     *  - Si YA es Device Owner -> reaplica el blindaje real (setUninstallBlocked,
     *    DNS privado off, VPN always-on) y lo confirma.
     *  - Si NO lo es -> muestra el comando ADB exacto, con opcion de copiarlo o
     *    abrir la guia. (Honesto: no simula algo que el sistema no permite.)
     */
    private fun onDeviceOwner() {
        if (DeviceOwnerManager.isOwner(this)) {
            try { DeviceOwnerManager.applyHardening(this) } catch (_: Exception) {}
            refresh()
            AlertDialog.Builder(this)
                .setTitle(R.string.owner_active_title)
                .setMessage(R.string.owner_active_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            val cmd = getString(R.string.owner_adb_cmd)
            AlertDialog.Builder(this)
                .setTitle(R.string.owner_needs_adb_title)
                .setMessage(getString(R.string.owner_needs_adb_msg, cmd))
                .setPositiveButton(R.string.owner_copy) { _, _ ->
                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                    toast(getString(R.string.owner_copied))
                }
                .setNeutralButton(R.string.owner_how) { _, _ -> openUrl(URL_OWNER_DOC) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    // ================================================================ lists

    private fun onUpdateLists() {
        toast(getString(R.string.lists_downloading))
        setUpdateEnabled(false)
        Thread {
            val (ok, msg) = try {
                BlocklistManager.download(this)
            } catch (e: Exception) {
                Pair(false, e.message ?: "error")
            }
            runOnUiThread {
                setUpdateEnabled(true)
                refresh()
                AlertDialog.Builder(this)
                    .setTitle(if (ok) R.string.lists_ok else R.string.lists_partial)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    private fun setUpdateEnabled(enabled: Boolean) {
        binding.btnUpdateLists.isEnabled = enabled
        binding.btnUpdateListsFull.isEnabled = enabled
        val a = if (enabled) 1f else 0.5f
        binding.btnUpdateLists.alpha = a
        binding.btnUpdateListsFull.alpha = a
    }

    // ================================================================ enlaces / notif settings

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("No se pudo abrir el enlace.")
        }
    }

    private fun openNotificationSettings() {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }

    // ================================================================ notificaciones (centro)

    /** Modelo simple para las notificaciones derivadas del estado actual. */
    private data class Notif(
        val icon: Int, val circle: Int, val tint: Int,
        val title: String, val text: String
    )

    private fun buildNotifs(): List<Notif> {
        val list = ArrayList<Notif>()
        val on = DnsVpnService.isRunning
        val pwd = PasswordManager.isSet(this)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminOn = dpm.isAdminActive(FilterDeviceAdminReceiver.component(this))
        val accOn = isAccessibilityEnabled()

        if (!on) list.add(Notif(R.drawable.ic_shield, R.drawable.circle_red, R.color.danger,
            "Proteccion inactiva", "Activa la proteccion para bloquear contenido no deseado."))
        if (!pwd) list.add(Notif(R.drawable.ic_lock, R.drawable.circle_red, R.color.danger,
            "Sin contrasena", "Define una contrasena para tener candado real contra el impulso."))
        if (!adminOn) list.add(Notif(R.drawable.ic_shield, R.drawable.circle_gold, R.color.gold,
            "Administrador de dispositivo inactivo", "Actívalo para evitar la desinstalacion facil."))
        if (!accOn) list.add(Notif(R.drawable.ic_accessibility, R.drawable.circle_gold, R.color.gold,
            "Guardian de accesibilidad inactivo", "Actívalo para frenar intentos de desactivar la app."))

        val blocked = Prefs.blockedCount(this)
        if (blocked > 0) list.add(Notif(R.drawable.ic_layers, R.drawable.circle_blue, R.color.brand_blue_2,
            "$blocked bloqueos aplicados", "El filtro ha detenido contenido no deseado."))

        if (list.isEmpty()) list.add(Notif(R.drawable.ic_shield, R.drawable.circle_green, R.color.success,
            "Todo protegido", getString(R.string.notif_empty)))
        return list
    }

    private fun actionableCount(): Int {
        var n = 0
        if (!DnsVpnService.isRunning) n++
        if (!PasswordManager.isSet(this)) n++
        return n
    }

    private fun showNotifications() {
        val sheet = BottomSheetDialog(this)
        val root = layoutInflater.inflate(R.layout.sheet_notifications, null)
        val list = root.findViewById<LinearLayout>(R.id.notifList)
        for (n in buildNotifs()) {
            val row = layoutInflater.inflate(R.layout.item_notification, list, false)
            val icon = row.findViewById<ImageView>(R.id.notifIcon)
            icon.setImageResource(n.icon)
            icon.setBackgroundResource(n.circle)
            icon.setColorFilter(ContextCompat.getColor(this, n.tint))
            row.findViewById<TextView>(R.id.notifTitle).text = n.title
            row.findViewById<TextView>(R.id.notifText).text = n.text
            list.addView(row)
        }
        sheet.setContentView(root)
        sheet.show()
    }

    // ================================================================ estado (refresh)

    private fun refresh() {
        val on = DnsVpnService.isRunning
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminOn = dpm.isAdminActive(FilterDeviceAdminReceiver.component(this))
        val accOn = isAccessibilityEnabled()
        val pwdOn = PasswordManager.isSet(this)
        val ownerOn = try { DeviceOwnerManager.isOwner(this) } catch (_: Exception) { false }
        val searchOn = accOn && SearchTermFilter.isLoaded()

        // --- Tarjeta de estado (Inicio) ---
        binding.imgShield.setImageResource(if (on) R.drawable.shield_green else R.drawable.shield_red)
        binding.tvStatus.text = getString(if (on) R.string.status_on else R.string.status_off)
        binding.tvStatus.setTextColor(color(if (on) R.color.success else R.color.danger))
        binding.tvStatusDesc.text =
            getString(if (on) R.string.status_desc_on else R.string.status_desc_off)

        binding.tvDomains.text = fmt(DomainFilter.domainCount().toLong())
        binding.tvWords.text = fmt(DomainFilter.keywordCount().toLong())
        binding.tvApplied.text = fmt(Prefs.blockedCount(this))

        setState(binding.tvPassword, pwdOn,
            getString(R.string.val_defined), getString(R.string.val_undefined))
        setState(binding.tvAdmin, adminOn,
            getString(R.string.val_active), getString(R.string.val_inactive))
        setState(binding.tvGuard, accOn,
            getString(R.string.val_active), getString(R.string.val_inactive))
        setState(binding.tvSearch, searchOn,
            getString(R.string.val_active), getString(R.string.val_inactive))
        setState(binding.tvOwner, ownerOn,
            getString(R.string.val_yes), getString(R.string.val_no))

        // --- Boton principal ---
        binding.tvToggle.text =
            getString(if (on) R.string.btn_deactivate else R.string.btn_activate)

        // --- Cabecera: escudo + badges ---
        binding.imgHeaderShield.setColorFilter(color(if (on) R.color.success else R.color.on_surface_dim))
        binding.statusBadge.setBackgroundResource(if (on) R.drawable.badge_green else R.drawable.badge_blue)
        binding.bellBadge.visibility = if (actionableCount() > 0) View.VISIBLE else View.GONE

        // --- Actividad ---
        binding.tvActBig.text = fmt(Prefs.blockedCount(this))
        binding.tvActDomains.text = fmt(DomainFilter.domainCount().toLong())
        binding.tvActWords.text = fmt(DomainFilter.keywordCount().toLong())
        buildEvents(on, pwdOn, adminOn, accOn, searchOn)

        // --- Listas ---
        binding.tvListsDomains.text = fmt(DomainFilter.domainCount().toLong())
        binding.tvListsWords.text = fmt(DomainFilter.keywordCount().toLong())
        val updated = Prefs.listsUpdatedAt(this)
        binding.tvListsUpdated.text =
            if (updated <= 0L) getString(R.string.lists_updated_never)
            else DateFormat.getDateFormat(this).format(Date(updated)) +
                    " " + DateFormat.getTimeFormat(this).format(Date(updated))

        // --- Ajustes: interruptor + icono de tema ---
        val dark = UiPrefs.isDark(this)
        binding.switchTheme.isChecked = dark
        binding.imgThemeIcon.setImageResource(if (dark) R.drawable.ic_moon else R.drawable.ic_sun)
    }

    /** Construye la lista de "eventos recientes" en la pestana Actividad. */
    private fun buildEvents(
        on: Boolean, pwd: Boolean, admin: Boolean, acc: Boolean, search: Boolean
    ) {
        val c = binding.actEventsContainer
        c.removeAllViews()
        addEvent(c, if (on) R.color.success else R.color.danger, "Proteccion",
            if (on) "Activa: filtrando DNS" else "Inactiva")
        addEvent(c, if (pwd) R.color.success else R.color.danger, "Contrasena",
            if (pwd) "Definida" else "Sin definir")
        addEvent(c, if (admin) R.color.success else R.color.warn, "Administrador de dispositivo",
            if (admin) "Activo" else "Inactivo")
        addEvent(c, if (acc) R.color.success else R.color.warn, "Guardian de accesibilidad",
            if (acc) "Activo" else "Inactivo")
        addEvent(c, if (search) R.color.success else R.color.warn, "Bloqueo de busquedas",
            if (search) "Activo (${SearchTermFilter.termCount()} terminos)" else "Inactivo", last = true)
    }

    private fun addEvent(parent: LinearLayout, colorRes: Int, title: String, sub: String, last: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(colorRes))
            }
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12) }
        }
        texts.addView(TextView(this).apply {
            text = title
            setTextColor(color(R.color.on_surface))
            textSize = 14.5f
        })
        texts.addView(TextView(this).apply {
            text = sub
            setTextColor(color(colorRes))
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(dot)
        row.addView(texts)
        parent.addView(row)
        if (!last) {
            parent.addView(View(this).apply {
                setBackgroundColor(color(R.color.outline))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { marginStart = dp(37) }
            })
        }
    }

    private fun setState(tv: TextView, active: Boolean, onText: String, offText: String) {
        tv.text = if (active) onText else offText
        tv.setTextColor(color(if (active) R.color.success else R.color.danger))
    }

    private fun color(res: Int) = ContextCompat.getColor(this, res)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(n: Long): String = "%,d".format(n).replace(',', '.')

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
