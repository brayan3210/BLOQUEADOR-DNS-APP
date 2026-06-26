package com.brayan.filtrocontenido.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.brayan.filtrocontenido.MainActivity
import com.brayan.filtrocontenido.R
import com.brayan.filtrocontenido.data.Prefs
import com.brayan.filtrocontenido.filter.BlocklistManager
import com.brayan.filtrocontenido.filter.DomainFilter
import com.brayan.filtrocontenido.net.Dns
import com.brayan.filtrocontenido.net.IpUdp
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * VPN local que intercepta y filtra el DNS, sin root.
 * Equivale a filtro.py escuchando en 127.0.0.1:53 en el PC:
 *   - parsea cada consulta DNS,
 *   - si el dominio esta bloqueado responde 0.0.0.0 / :: / NXDOMAIN,
 *   - si se permite, reenvia al upstream (1.1.1.1 / 1.0.0.1) por un socket protegido.
 */
class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.brayan.filtrocontenido.START"
        const val ACTION_STOP = "com.brayan.filtrocontenido.STOP"

        private const val CHANNEL_ID = "filtro_estado"
        private const val NOTIF_ID = 1001

        private const val VPN_ADDRESS = "10.215.173.1"
        private const val VIRTUAL_DNS = "10.215.173.2"

        // DNS publicos hardcodeados que tambien interceptamos (apps que ignoran el DNS del sistema).
        private val PUBLIC_DNS = listOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220",
            "94.140.14.14", "94.140.15.15"
        )

        @Volatile var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var pool: ExecutorService? = null

    @Volatile private var running = false
    private var inStream: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return

        if (!DomainFilter.isLoaded()) {
            try { BlocklistManager.load(this) } catch (_: Exception) {}
        }

        startForegroundNotification()

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            .setMtu(1500)
            .setBlocking(true)

        for (ip in PUBLIC_DNS) {
            try { builder.addRoute(ip, 32) } catch (_: Exception) {}
        }
        // No filtrar nuestro propio trafico (el reenvio al upstream).
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        val iface = try { builder.establish() } catch (e: Exception) { null }
        if (iface == null) {
            stopVpn()
            return
        }
        vpnInterface = iface
        inStream = FileInputStream(iface.fileDescriptor)
        outStream = FileOutputStream(iface.fileDescriptor)

        running = true
        isRunning = true
        Prefs.setEnabled(this, true)

        pool = Executors.newFixedThreadPool(8)
        worker = Thread({ readLoop() }, "dns-vpn-loop").apply { start() }
    }

    private fun readLoop() {
        val input = inStream ?: return
        val buffer = ByteArray(32_767)
        while (running) {
            val n = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) break else return
            }
            if (n <= 0) continue
            val packet = buffer.copyOf(n)
            pool?.execute { handlePacket(packet, n) }
        }
    }

    private fun handlePacket(packet: ByteArray, len: Int) {
        try {
            val dg = IpUdp.parse(packet, len) ?: return
            if (dg.dstPort != 53) return  // solo DNS

            val question = Dns.parseQuestion(dg.payload)
            if (question != null) {
                val reason = DomainFilter.blockReason(question.name)
                if (reason != null) {
                    Prefs.incBlocked(this)
                    val resp = Dns.buildBlocked(dg.payload, question.qtype, question.questionEnd)
                    writePacket(IpUdp.build(dg.dstIp, dg.srcIp, dg.dstPort, dg.srcPort, resp))
                    return
                }
            }

            // Permitido: reenviar al upstream real.
            val resp = forwardUpstream(dg.payload) ?: return
            writePacket(IpUdp.build(dg.dstIp, dg.srcIp, dg.dstPort, dg.srcPort, resp))
        } catch (_: Exception) { }
    }

    private fun forwardUpstream(query: ByteArray): ByteArray? {
        val upstreams = listOf(Prefs.upstream1(this), Prefs.upstream2(this))
        for (ip in upstreams) {
            repeat(2) {
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    protect(socket)               // imprescindible: evita el bucle por la VPN
                    socket!!.soTimeout = 3000
                    val addr = InetAddress.getByName(ip)  // IP literal: sin resolucion DNS
                    socket!!.send(DatagramPacket(query, query.size, addr, 53))
                    val buf = ByteArray(4096)
                    val dp = DatagramPacket(buf, buf.size)
                    socket!!.receive(dp)
                    return buf.copyOf(dp.length)
                } catch (_: Exception) {
                } finally {
                    socket?.close()
                }
            }
        }
        return null
    }

    private fun writePacket(packet: ByteArray) {
        val out = outStream ?: return
        synchronized(writeLock) {
            try {
                out.write(packet)
                out.flush()
            } catch (_: Exception) { }
        }
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        try { pool?.shutdownNow() } catch (_: Exception) {}
        pool = null
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** El sistema llama esto si otra VPN toma el control o el usuario revoca el permiso. */
    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    // ---- Notificacion persistente ----

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}
