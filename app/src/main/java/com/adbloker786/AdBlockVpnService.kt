package com.adbloker786

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AdBlockVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val preferences by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val descriptor = try {
            Builder().setSession("Adbloker 786")
                .addAddress(TUN_ADDRESS, 32)
                .addRoute(DNS_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .setMtu(1500)
                .setBlocking(true)
                .establish()
        } catch (_: Exception) {
            null
        }

        if (descriptor == null) {
            preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        tunnel = descriptor
        running = true
        preferences.edit().putBoolean(KEY_ENABLED, true).apply()
        thread(name = "adbloker-dns-tun", priority = Thread.NORM_PRIORITY) { pump(descriptor) }
        return START_STICKY
    }

    private fun pump(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running) {
            try {
                val count = input.read(buffer)
                if (count <= 0) continue
                val request = DnsPacket.parse(buffer, count) ?: continue
                val mode = preferences.getString(KEY_MODE, "advanced") ?: "advanced"
                val customBlocks = preferences.getString(KEY_CUSTOM_BLOCKS, "").orEmpty()
                val allowRules = preferences.getString(KEY_ALLOW_RULES, "").orEmpty()
                val blocked = FilterEngine.isBlocked(request.host, mode, customBlocks, allowRules)
                val dnsResponse = if (blocked) DnsPacket.errorResponse(request, 3) else
                    forwardDns(request) ?: DnsPacket.errorResponse(request, 2)
                output.write(DnsPacket.buildIpv4UdpResponse(request, dnsResponse))
                recordQuery(blocked)
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun forwardDns(request: DnsPacket.Request): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = 3500
                val upstream = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(request.dnsMessage, request.dnsMessage.size, upstream, 53))
                val responseBuffer = ByteArray(4096)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                if (response.length < 12 || response.port != 53) return null
                val dns = response.data.copyOf(response.length)
                val sameId = dns[0] == request.dnsMessage[0] && dns[1] == request.dnsMessage[1]
                val isResponse = (dns[2].toInt() and 0x80) != 0
                if (!sameId || !isResponse || dns.size > 1400) null else dns
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recordQuery(blocked: Boolean) {
        val editor = preferences.edit()
        editor.putLong(KEY_QUERY_COUNT, preferences.getLong(KEY_QUERY_COUNT, 0L) + 1L)
        if (blocked) editor.putLong(KEY_BLOCKED_COUNT, preferences.getLong(KEY_BLOCKED_COUNT, 0L) + 1L)
        editor.apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Adbloker protection", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when DNS ad and tracker filtering is active"
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Adbloker 786 is active")
            .setContentText("DNS ad and tracker filtering is running")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Turn off", stopPendingIntent).build())
            .build()
    }

    override fun onRevoke() {
        closeTunnel()
        super.onRevoke()
        stopSelf()
    }

    override fun onDestroy() {
        closeTunnel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun closeTunnel() {
        running = false
        preferences.edit().putBoolean(KEY_ENABLED, false).apply()
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
    }

    companion object {
        const val ACTION_STOP = "com.adbloker786.action.STOP"
        private const val PREFS = "adbloker786"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
        private const val KEY_CUSTOM_BLOCKS = "custom_blocks"
        private const val KEY_ALLOW_RULES = "allow_rules"
        private const val KEY_QUERY_COUNT = "query_count"
        private const val KEY_BLOCKED_COUNT = "blocked_count"
        private const val TUN_ADDRESS = "10.77.0.1"
        private const val DNS_ADDRESS = "10.77.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val CHANNEL_ID = "adbloker_protection"
        private const val NOTIFICATION_ID = 786
    }
}