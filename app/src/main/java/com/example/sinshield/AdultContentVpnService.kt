package com.example.sinshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local split-tunnel VPN that captures classic DNS only. Allowed questions are forwarded to the
 * current network's resolver; blocked questions receive NXDOMAIN and never leave the device.
 */
class AdultContentVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var matcher = AdultDomainMatcher.empty()
    private var upstreamServers: List<InetAddress> = emptyList()
    private val packetIdentification = AtomicInteger(1)
    private val recentlyReported = LinkedHashMap<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var stopping = false
    private var reconnectAttempt = 0
    private val healthHeartbeat = object : Runnable {
        override fun run() {
            if (!isRunning || stopping) return
            ProtectionHealthMonitor.recordVpnRunning(this@AdultContentVpnService)
            handler.postDelayed(this, HEALTH_HEARTBEAT_INTERVAL_MS)
        }
    }
    private val reconnect = Runnable {
        if (!stopping && tunnel == null) establishTunnel()
    }

    private fun quickStart(intent: Intent): Int? {
        return when (intent.action) {
            ACTION_STOP -> {
                stopVpn()
                Log.i(TAG, "Stopped VPN")
                START_NOT_STICKY
            }

            ACTION_RELOAD_LIST -> {
                matcher = AdultDomainListRepository.load(this)
                Log.i(TAG, "Reloaded ${matcher.size} blocked domains")
                START_STICKY
            }

            ACTION_REFRESH_STATUS -> {
                recordAlwaysOnStatus()
                broadcastState(isRunning)
                Log.i(TAG, "Refreshed VPN status")
                START_STICKY
            }

            else -> null
        }
    }

    fun regularStart(): Int {
        val activeNotification = notification(getString(R.string.vpn_notification_active))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                activeNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, activeNotification)
        }
        Log.i(TAG, "Started foreground service")

        matcher = AdultDomainListRepository.load(this)
        recordAlwaysOnStatus()
        establishTunnel()
        // The matcher is already loaded above. Only swap it out when a refresh actually downloaded
        // a newer list (a distinct instance); when the list is still fresh, refreshIfStale hands
        // back the same cached matcher, so this avoids a redundant reassignment and re-parse.
        AdultDomainListRepository.refreshIfStale(this) { result ->
            result.matcher?.takeIf { it !== matcher }?.let { matcher = it }
        }
        return START_STICKY
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { return START_NOT_STICKY }

        quickStart(intent)?.let { return it }

        if (tunnel != null) { return START_STICKY }

        return regularStart()
    }

    private fun establishTunnel() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val underlyingNetwork = connectivity.activeNetwork
        // Reuse the real network's resolvers so answers match what the user would get without the
        // VPN. Exclude our own virtual DNS address so a forwarded query can never loop back in.
        val networkDns = connectivity.getLinkProperties(underlyingNetwork)
            ?.dnsServers
            .orEmpty()
            .filterIsInstance<Inet4Address>()
            .filterNot { it.hostAddress == VPN_DNS_ADDRESS }
        // Append public resolvers as a fallback for networks that expose no usable DNS server.
        upstreamServers = (networkDns + FALLBACK_DNS.map(InetAddress::getByName))
            .distinctBy(InetAddress::getHostAddress)

        // Split tunnel: route ONLY traffic to the virtual DNS address into the tunnel. Everything
        // else keeps its normal path, so this captures classic DNS without touching web traffic.
        val builder = Builder()
            .setSession(getString(R.string.website_protection))
            .setMtu(MTU)
            .addAddress(VPN_CLIENT_ADDRESS, 32)
            .addDnsServer(VPN_DNS_ADDRESS)
            .addRoute(VPN_DNS_ADDRESS, 32)
            .setBlocking(true)
        // Keep SinSheld's own traffic out of the tunnel so forwarding sockets reach the network
        // directly rather than recursing through this service.
        runCatching { builder.addDisallowedApplication(packageName) }
        tunnel = runCatching { builder.establish() }
            .onFailure { Log.e(TAG, "Could not establish the VPN interface", it) }
            .getOrNull()
        if (tunnel == null) {
            Log.e(TAG, "Android did not establish the VPN interface")
            tunnelFailed()
            return
        }

        reconnectAttempt = 0
        isRunning = true
        ProtectionHealthMonitor.recordVpnRunning(this)
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(getString(R.string.vpn_notification_active))
        )
        handler.removeCallbacks(healthHeartbeat)
        healthHeartbeat.run()
        broadcastState(true)
        worker = Thread(::processPackets, "SinSheld-DNS").apply { start() }
        Log.i(TAG, "DNS VPN active with ${matcher.size} blocked domains")
    }

    private fun processPackets() {
        val descriptor = tunnel
            ?: throw IllegalStateException("VPN tunnel is not established")

        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packetBuffer = ByteArray(32_767)
        try {

            while (!Thread.currentThread().isInterrupted) {
                val length = input.read(packetBuffer)

                if (length > 0) {
                    Ipv4UdpPacketCodec.parse(packetBuffer, length)
                        ?.takeIf { it.destinationPort == DNS_PORT }
                        ?.let { request ->
                            val queryName = DnsMessageCodec.queryName(request.payload)
                            val blocked = queryName?.let(matcher::isBlocked) == true

                            val dnsResponse = if (blocked) {
                                DnsMessageCodec.nxdomainResponse(request.payload)
                            } else {
                                forward(request.payload)
                            }

                            dnsResponse?.let { responsePayload ->
                                if (blocked) {
                                    reportBlockedDomain(queryName)
                                }

                                val response = Ipv4UdpPacketCodec.response(
                                    request,
                                    responsePayload,
                                    packetIdentification.getAndIncrement()
                                )

                                output.write(response)
                            }
                        }
                }
            }
        } catch (failure: Throwable) {
            if (tunnel != null) Log.w(TAG, "DNS packet loop stopped", failure)
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
            // stopVpn clears the tunnel before interrupting the thread. If it is still present,
            // the packet loop ended unexpectedly and website protection is no longer functional.
            handler.post { packetLoopEnded(descriptor) }
        }
    }

    private fun packetLoopEnded(descriptor: ParcelFileDescriptor) {
        if (stopping || tunnel !== descriptor) return
        Log.w(TAG, "DNS packet loop ended unexpectedly; reconnecting")
        tunnel = null
        worker = null
        runCatching { descriptor.close() }
        tunnelFailed()
    }

    private fun tunnelFailed() {
        isRunning = false
        handler.removeCallbacks(healthHeartbeat)
        broadcastState(false)
        ProtectionHealthMonitor.recordVpnFailure(this)
        if (stopping) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(getString(R.string.vpn_notification_reconnecting))
        )
        val delay = VpnReconnectPolicy.delayForAttempt(reconnectAttempt)
        reconnectAttempt++
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, delay)
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in upstreamServers) {
            try {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) return@use

                    socket.soTimeout = DNS_TIMEOUT_MS
                    socket.send(
                        DatagramPacket(query, query.size, server, DNS_PORT)
                    )

                    val response = ByteArray(MAX_DNS_RESPONSE_BYTES)
                    val packet = DatagramPacket(response, response.size)

                    socket.receive(packet)

                    return response.copyOf(packet.length)
                }
            } catch (_: SocketTimeoutException) {
                // Try next server
            } catch (failure: Exception) {
                Log.w(
                    TAG,
                    "DNS forwarding to ${server.hostAddress} failed",
                    failure
                )
            }
        }

        return DnsMessageCodec.serverFailureResponse(query)
    }

    private fun reportBlockedDomain(domain: String) {
        val now = System.currentTimeMillis()
        // A single page load fires many DNS queries for the same blocked host. Coalesce them so the
        // user sees one overlay/notification per domain per cooldown instead of a burst.
        synchronized(recentlyReported) {
            val lastReported = recentlyReported[domain] ?: 0L
            if (now - lastReported < REPORT_COOLDOWN_MS) return
            recentlyReported[domain] = now
            // Cap the map by evicting the oldest entry (LinkedHashMap preserves insertion order),
            // bounding memory over a long-running session.
            if (recentlyReported.size > MAX_RECENT_REPORTS) {
                recentlyReported.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
        }
        sendBroadcast(
            Intent(ACTION_ADULT_DOMAIN_BLOCKED)
                .setPackage(packageName)
                .putExtra(EXTRA_DOMAIN, domain)
        )
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(getString(R.string.vpn_notification_blocked))
        )
        Log.i(TAG, "Blocked adult-content domain")
    }

    private fun notification(content: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.website_protection),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopVpn = PendingIntent.getService(
            this,
            1,
            Intent(this, AdultContentVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.sinshield_notification)
            .setColor(getColor(R.color.sinshield_primary))
            .setContentTitle(getString(R.string.website_protection_active))
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isAlwaysOn) {
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.stop), stopVpn).build()
            )
        }
        return builder.build()
    }

    private fun stopVpn() {
        stopping = true
        handler.removeCallbacks(reconnect)
        handler.removeCallbacks(healthHeartbeat)
        val descriptor = tunnel
        tunnel = null
        worker?.interrupt()
        worker = null
        runCatching { descriptor?.close() }
        isRunning = false
        broadcastState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastState(running: Boolean) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_RUNNING, running)
                .putExtra(
                    EXTRA_ALWAYS_ON,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlwaysOn
                )
        )
    }

    private fun recordAlwaysOnStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ProtectionHealthMonitor.recordVpnAlwaysOn(this, isAlwaysOn)
        }
    }

    override fun onRevoke() {
        ProtectionHealthMonitor.setVpnExpected(this, false)
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        // Do not call stopSelf() here. If Android is reclaiming this sticky service, explicitly
        // stopping it would cancel the restart that START_STICKY requested.
        stopping = true
        handler.removeCallbacks(reconnect)
        handler.removeCallbacks(healthHeartbeat)
        val descriptor = tunnel
        tunnel = null
        worker?.interrupt()
        worker = null
        runCatching { descriptor?.close() }
        isRunning = false
        broadcastState(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ADULT_DOMAIN_BLOCKED =
            "com.example.sinshield.action.ADULT_DOMAIN_BLOCKED"
        const val ACTION_STATE_CHANGED = "com.example.sinshield.action.VPN_STATE_CHANGED"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_ALWAYS_ON = "always_on"
        private const val ACTION_START = "com.example.sinshield.action.START_VPN"
        private const val ACTION_STOP = "com.example.sinshield.action.STOP_VPN"
        private const val ACTION_RELOAD_LIST = "com.example.sinshield.action.RELOAD_VPN_LIST"
        private const val ACTION_REFRESH_STATUS = "com.example.sinshield.action.REFRESH_VPN_STATUS"
        private const val TAG = "SinSheldVpn"
        private const val NOTIFICATION_CHANNEL = "sinshield_vpn"
        private const val NOTIFICATION_ID = 2
        private const val VPN_CLIENT_ADDRESS = "10.77.0.1"
        private const val VPN_DNS_ADDRESS = "10.77.0.2"
        private const val MTU = 1_500
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 1_500
        private const val MAX_DNS_RESPONSE_BYTES = 4_096
        private const val REPORT_COOLDOWN_MS = 4_000L
        private const val MAX_RECENT_REPORTS = 256
        private const val HEALTH_HEARTBEAT_INTERVAL_MS = 5L * 60L * 1_000L
        private val FALLBACK_DNS = listOf("1.1.1.1", "8.8.8.8")

        @Volatile var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ProtectionHealthMonitor.setVpnExpected(context, true)
            ContextCompat.startForegroundService(
                context,
                Intent(context, AdultContentVpnService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            ProtectionHealthMonitor.setVpnExpected(context, false)
            context.startService(
                Intent(context, AdultContentVpnService::class.java).setAction(ACTION_STOP)
            )
        }

        fun reloadList(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, AdultContentVpnService::class.java).setAction(ACTION_RELOAD_LIST)
            )
        }

        fun refreshStatus(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, AdultContentVpnService::class.java).setAction(ACTION_REFRESH_STATUS)
            )
        }
    }
}

internal object VpnReconnectPolicy {
    private val delaysMs = longArrayOf(1_000L, 2_000L, 5_000L, 15_000L, 60_000L)

    fun delayForAttempt(attempt: Int): Long =
        delaysMs[attempt.coerceIn(0, delaysMs.lastIndex)]
}
