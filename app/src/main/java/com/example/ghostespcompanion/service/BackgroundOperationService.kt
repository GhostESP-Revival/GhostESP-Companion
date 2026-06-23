package com.example.ghostespcompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.ghostespcompanion.MainActivity
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.data.LocationHelper
import com.example.ghostespcompanion.data.repository.GhostRepository
import com.example.ghostespcompanion.data.repository.PhoneWardriveStats
import com.example.ghostespcompanion.data.repository.SettingsManager
import com.example.ghostespcompanion.domain.model.GhostResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundOperationService : Service() {
    @Inject lateinit var locationHelper: LocationHelper
    @Inject lateinit var ghostRepository: GhostRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeOperations = linkedMapOf<String, String>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var phoneLocationJob: Job? = null
    private var statsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPLACE -> {
                activeOperations.clear()
                addOperation(intent)
            }
            ACTION_START -> addOperation(intent)
            ACTION_STOP -> activeOperations.remove(intent.getStringExtra(EXTRA_OPERATION))
            ACTION_STOP_ALL -> activeOperations.clear()
        }

        if (activeOperations.isEmpty()) {
            stopPhoneLocationUpdates()
            stopStatsUpdates()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        acquireWakeLock()
        syncPhoneLocationUpdates()
        syncStatsUpdates()
        updateForegroundNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPhoneLocationUpdates()
        stopStatsUpdates()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun addOperation(intent: Intent) {
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: operation
        activeOperations[operation] = title
    }

    private fun updateForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (activeOperations.containsKey(OP_PHONE_WARDRIVE) && locationHelper.hasLocationPermission()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (activeOperations.size > 1) {
            "GhostESP: ${activeOperations.size} operations"
        } else {
            "GhostESP: ${activeOperations.values.first()}"
        }

        val statsText = buildStatsText()
        val text = if (statsText != null) statsText else activeOperations.values.joinToString()

        return NotificationCompat.Builder(this, SettingsManager.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun buildStatsText(): String? {
        val parts = mutableListOf<String>()

        if (activeOperations.containsKey(OP_WARDRIVE) || activeOperations.containsKey(OP_BLE_WARDRIVE)) {
            val stats = lastWardriveStats
            if (stats != null) {
                val aps = if (activeOperations.containsKey(OP_BLE_WARDRIVE)) stats.bleDevices else stats.accessPoints
                parts.add("APs: $aps")
                parts.add("Logged: ${stats.loggedOk}/${stats.logAttempts}")
                parts.add("GPS: ${stats.gpsFixStatus}")
                parts.add("Sats: ${stats.gpsSatellites}")
                val uptime = "%d:%02d".format(stats.uptimeMinutes, stats.uptimeSeconds)
                parts.add("Time: $uptime")
            } else {
                return activeOperations.values.joinToString()
            }
        }

        if (activeOperations.containsKey(OP_PHONE_WARDRIVE)) {
            val stats = lastPhoneWardriveStats
            parts.add("APs: ${stats.accessPoints}")
            parts.add("GPS-tagged: ${stats.locatedObservations}/${stats.observations}")
            parts.add("GPS fix: ${if (stats.gpsFix) "Yes" else "No"}")
        }

        return if (parts.isEmpty()) null else parts.joinToString("  |  ")
    }

    @Volatile private var lastWardriveStats: GhostResponse.WardriveStats? = null
    @Volatile private var lastPhoneWardriveStats: PhoneWardriveStats = PhoneWardriveStats()

    private fun syncStatsUpdates() {
        if (statsJob?.isActive == true) return
        statsJob = scope.launch {
            combine(
                ghostRepository.wardriveStats,
                ghostRepository.phoneWardriveStats
            ) { wardrive, phone ->
                lastWardriveStats = wardrive
                lastPhoneWardriveStats = phone
            }.collect {
                updateForegroundNotification()
            }
        }
    }

    private fun stopStatsUpdates() {
        statsJob?.cancel()
        statsJob = null
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GhostESPCompanion:BackgroundOperation"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun syncPhoneLocationUpdates() {
        if (!activeOperations.containsKey(OP_PHONE_WARDRIVE)) {
            stopPhoneLocationUpdates()
            return
        }
        if (phoneLocationJob?.isActive == true) return

        phoneLocationJob = scope.launch {
            try {
                locationHelper.getLocationUpdates().collect { location ->
                    ghostRepository.updatePhoneLocation(location)
                }
            } catch (_: SecurityException) {
                // Permission can be revoked while the foreground service is active.
            }
        }
    }

    private fun stopPhoneLocationUpdates() {
        phoneLocationJob?.cancel()
        phoneLocationJob = null
    }

    companion object {
        private const val ACTION_START = "com.example.ghostespcompanion.action.START_BACKGROUND_OPERATION"
        private const val ACTION_REPLACE = "com.example.ghostespcompanion.action.REPLACE_BACKGROUND_OPERATION"
        private const val ACTION_STOP = "com.example.ghostespcompanion.action.STOP_BACKGROUND_OPERATION"
        private const val ACTION_STOP_ALL = "com.example.ghostespcompanion.action.STOP_ALL_BACKGROUND_OPERATIONS"
        private const val EXTRA_OPERATION = "operation"
        private const val EXTRA_TITLE = "title"
        private const val NOTIFICATION_ID = 4001

        const val OP_DEAUTH = "deauth"
        const val OP_BEACON_SPAM = "beacon_spam"
        const val OP_KARMA = "karma"
        const val OP_TRACK_AP = "track_ap"
        const val OP_TRACK_STA = "track_sta"
        const val OP_PACKET_CAPTURE = "packet_capture"
        const val OP_BLE_SPAM = "ble_spam"
        const val OP_TRACK_GATT = "track_gatt"
        const val OP_TRACK_FLIPPER = "track_flipper"
        const val OP_AIRTAG_SPOOF = "airtag_spoof"
        const val OP_IR_DAZZLER = "ir_dazzler"
        const val OP_BADUSB = "badusb"
        const val OP_BADUSB_KEYBOARD = "badusb_keyboard"
        const val OP_BADUSB_JIGGLER = "badusb_jiggler"
        const val OP_GPS_INFO = "gps_info"
        const val OP_WARDRIVE = "wardrive"
        const val OP_BLE_WARDRIVE = "ble_wardrive"
        const val OP_PHONE_WARDRIVE = "phone_wardrive"
        const val OP_AERIAL_TRACK = "aerial_track"
        const val OP_AERIAL_SPOOF = "aerial_spoof"
        const val OP_PORTAL = "portal"

        fun replaceOperation(context: Context, operation: String, title: String) {
            context.startForegroundIntent(ACTION_REPLACE, operation, title)
        }

        fun startOperation(context: Context, operation: String, title: String) {
            context.startForegroundIntent(ACTION_START, operation, title)
        }

        fun stopOperation(context: Context, operation: String) {
            val intent = Intent(context, BackgroundOperationService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_OPERATION, operation)
            }
            context.startService(intent)
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, BackgroundOperationService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            context.startService(intent)
        }

        private fun Context.startForegroundIntent(actionName: String, operation: String, title: String) {
            val intent = Intent(this, BackgroundOperationService::class.java).apply {
                action = actionName
                putExtra(EXTRA_OPERATION, operation)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
