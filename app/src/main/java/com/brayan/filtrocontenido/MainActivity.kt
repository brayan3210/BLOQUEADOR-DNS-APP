package com.brayan.filtrocontenido

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.brayan.filtrocontenido.admin.DeviceOwnerManager
import com.brayan.filtrocontenido.admin.FilterDeviceAdminReceiver
import com.brayan.filtrocontenido.data.Prefs
import com.brayan.filtrocontenido.databinding.ActivityMainBinding
import com.brayan.filtrocontenido.filter.BlocklistManager
import com.brayan.filtrocontenido.filter.DomainFilter
import com.brayan.filtrocontenido.lock.PasswordManager
import com.brayan.filtrocontenido.vpn.DnsVpnService

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GUARD_BLOCK = "guard_block"
        private const val UNLOCK_WINDOW_MS = 5 * 60 * 1000L
    }

    private lateinit var binding: ActivityMainBinding

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

        // Cargar listas en segundo plano la primera vez.
        if (!DomainFilter.isLoaded()) {
            Thread {
                try { BlocklistManager.load(this) } catch (_: Exception) {}
                runOnUiThread { refresh() }
            }.start()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnToggle.setOnClickListener { onToggle() }
        binding.btnPassword.setOnClickListener { onSetPassword() }
        binding.btnDeviceAdmin.setOnClickListener { onDeviceAdmin() }
        binding.btnAccessibility.setOnClickListener { onAccessibility() }
        binding.btnUpdateLists.setOnClickListener { onUpdateLists() }

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

    // ---------------------------------------------------------------- toggle

    private fun onToggle() {
        if (DnsVpnService.isRunning) {
            // Desactivar requiere contrasena (friccion).
            if (!PasswordManager.isSet(this)) {
                // Sin contrasena no hay candado real: permitir, pero avisar.
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
        // Sugerir reforzar la proteccion.
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

    // ---------------------------------------------------------------- password

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

    // ---------------------------------------------------------------- admin / accessibility

    private fun onDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = FilterDeviceAdminReceiver.component(this)
        if (dpm.isAdminActive(component)) {
            toast(getString(R.string.admin_already))
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explain)
            )
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
        return flat.contains(packageName + "/" )
    }

    // ---------------------------------------------------------------- lists

    private fun onUpdateLists() {
        toast(getString(R.string.lists_downloading))
        binding.btnUpdateLists.isEnabled = false
        Thread {
            val (ok, msg) = try {
                BlocklistManager.download(this)
            } catch (e: Exception) {
                Pair(false, e.message ?: "error")
            }
            runOnUiThread {
                binding.btnUpdateLists.isEnabled = true
                refresh()
                AlertDialog.Builder(this)
                    .setTitle(if (ok) R.string.lists_ok else R.string.lists_partial)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    // ---------------------------------------------------------------- ui state

    private fun refresh() {
        val on = DnsVpnService.isRunning
        binding.tvStatus.text =
            if (on) getString(R.string.status_on) else getString(R.string.status_off)
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.green else R.color.red)
        )
        binding.btnToggle.text =
            getString(if (on) R.string.btn_deactivate else R.string.btn_activate)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminOn = dpm.isAdminActive(FilterDeviceAdminReceiver.component(this))
        val accOn = isAccessibilityEnabled()
        val pwdOn = PasswordManager.isSet(this)
        val ownerOn = try { DeviceOwnerManager.isOwner(this) } catch (_: Exception) { false }

        binding.tvStats.text = buildString {
            append("Dominios: ").append(DomainFilter.domainCount())
            append("  •  Palabras: ").append(DomainFilter.keywordCount()).append('\n')
            append("Bloqueos: ").append(Prefs.blockedCount(this@MainActivity)).append('\n')
            append("Contrasena: ").append(if (pwdOn) "definida" else "SIN definir").append('\n')
            append("Admin dispositivo: ").append(if (adminOn) "activo" else "inactivo").append('\n')
            append("Guardian accesibilidad: ").append(if (accOn) "activo" else "inactivo").append('\n')
            append("Device Owner (capa 3): ").append(if (ownerOn) "ACTIVO" else "no")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
