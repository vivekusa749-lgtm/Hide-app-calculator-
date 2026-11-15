package com.example.applocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.HideCalcApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppLockerMonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "hcx_app_locker_channel"
        private const val NOTIFICATION_ID = 4107
        private const val POLL_INTERVAL_MS = 550L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lockedPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null
    private var lastGatePackage: String? = null
    private var lastGateTimeMs: Long = 0L
    private var monitorJob: Job? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                AppLockerSessionBridge.clearAll()
                lastForegroundPackage = null
                lastGatePackage = null
                lastGateTimeMs = 0L
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val container = (application as HideCalcApplication).container
        serviceScope.launch {
            container.vaultDao.observeLockedApps().collectLatest { apps ->
                lockedPackages = apps.filter { it.isLocked }.map { it.packageName }.toSet()
                updateNotification(lockedPackages.size)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(lockedPackages.size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (monitorJob == null || monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch {
                monitorForegroundApps()
            }
        }
        return START_STICKY
    }

    private suspend fun monitorForegroundApps() {
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        while (serviceScope.isActive) {
            delay(POLL_INTERVAL_MS)
            if (lockedPackages.isEmpty() || !Settings.canDrawOverlays(this)) continue

            val now = System.currentTimeMillis()
            val events = runCatching {
                usageStats.queryEvents(now - 3_000L, now)
            }.getOrNull() ?: continue

            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isForegroundEvent && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPkg = event.packageName
                }
            }

            if (latestPkg != null && latestPkg != packageName) {
                if (latestPkg != lastForegroundPackage) {
                    // User switched away from the previously verified app -> require PIN next time
                    lastForegroundPackage?.let { previous ->
                        if (previous != latestPkg) {
                            AppLockerSessionBridge.clearPackage(previous)
                        }
                    }
                    lastForegroundPackage = latestPkg
                }

                if (lockedPackages.contains(latestPkg) &&
                    !AppLockerSessionBridge.isTemporarilyUnlocked(latestPkg)
                ) {
                    val cooldownActive = latestPkg == lastGatePackage && (now - lastGateTimeMs) < 1_500L
                    if (!cooldownActive) {
                        lastGatePackage = latestPkg
                        lastGateTimeMs = now
                        launchGateForPackage(latestPkg)
                    }
                }
            }
        }
    }

    private fun launchGateForPackage(targetPackage: String) {
        val gateIntent = Intent(this, AppLockGateActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(AppLockGateActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        runCatching { startActivity(gateIntent) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_locker_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.app_locker_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(count))
    }

    private fun buildNotification(lockedCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.app_locker_notif_title))
            .setContentText(getString(R.string.app_locker_notif_text, lockedCount))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
