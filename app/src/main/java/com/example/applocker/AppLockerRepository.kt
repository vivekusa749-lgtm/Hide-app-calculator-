package com.example.applocker

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.LockedAppEntity
import com.example.data.VaultDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

data class AppLockerPermissionsState(
    val hasUsageStatsAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true
) {
    val allCorePermissionsGranted: Boolean
        get() = hasUsageStatsAccess && hasOverlayPermission
}

/**
 * Runtime session cache for apps temporarily unlocked after passing the AppLockGateActivity PIN.
 */
object AppLockerSessionBridge {
    private val unlockedPackages = mutableSetOf<String>()
    @Volatile
    var lastVerifiedPackage: String? = null

    @Synchronized
    fun markUnlocked(packageName: String) {
        unlockedPackages.add(packageName)
        lastVerifiedPackage = packageName
    }

    @Synchronized
    fun isTemporarilyUnlocked(packageName: String): Boolean = unlockedPackages.contains(packageName)

    @Synchronized
    fun clearPackage(packageName: String) {
        unlockedPackages.remove(packageName)
    }

    @Synchronized
    fun clearAll() {
        unlockedPackages.clear()
        lastVerifiedPackage = null
    }
}

class AppLockerRepository(
    private val context: Context,
    private val dao: VaultDao
) {
    fun observeLockedApps(): Flow<List<LockedAppEntity>> = dao.observeLockedApps()

    suspend fun setAppLocked(app: InstalledAppItem, lock: Boolean) = withContext(Dispatchers.IO) {
        if (lock) {
            dao.upsertLockedApp(
                LockedAppEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    isLocked = true
                )
            )
        } else {
            dao.removeLockedApp(app.packageName)
            AppLockerSessionBridge.clearPackage(app.packageName)
        }
    }

    suspend fun getLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val selfPackage = context.packageName
        val seen = HashSet<String>()
        resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == selfPackage || !seen.add(pkg)) return@mapNotNull null
            val appInfo = info.activityInfo.applicationInfo
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            InstalledAppItem(
                packageName = pkg,
                appName = label,
                icon = icon,
                isSystemApp = isSystem
            )
        }.sortedWith(compareBy<InstalledAppItem> { it.isSystemApp }.thenBy { it.appName.lowercase() })
    }

    fun checkPermissions(): AppLockerPermissionsState {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        val hasUsageStats = mode == AppOpsManager.MODE_ALLOWED
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return AppLockerPermissionsState(
            hasUsageStatsAccess = hasUsageStats,
            hasOverlayPermission = hasOverlay,
            hasNotificationPermission = hasNotif
        )
    }

    fun startMonitorService() {
        val perms = checkPermissions()
        if (!perms.allCorePermissionsGranted) return
        val intent = Intent(context, AppLockerMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopMonitorService() {
        context.stopService(Intent(context, AppLockerMonitorService::class.java))
        AppLockerSessionBridge.clearAll()
    }
}
