package com.example.ui.applocker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.applocker.InstalledAppItem
import com.example.security.VaultSessionManager
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AppLockerScreen(
    viewModel: AppLockerViewModel,
    sessionManager: VaultSessionManager
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accents = LocalVaultAccents.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission badges whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master Toggle Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianCard)
                    .border(
                        1.dp,
                        if (uiState.isProtectionEnabled) accents.statusGood.copy(alpha = 0.55f) else accents.cardBorder,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accents.neonPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "App Locker protection",
                                tint = accents.neonPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "App Locker Service",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${uiState.lockedPackageNames.size} app(s) selected for PIN gate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isProtectionEnabled,
                        enabled = uiState.permissions.allCorePermissionsGranted,
                        onCheckedChange = { enabled ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !uiState.permissions.hasNotificationPermission &&
                                enabled
                            ) {
                                sessionManager.allowTemporaryExternalActivity()
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setProtectionEnabled(enabled)
                        },
                        modifier = Modifier.testTag("applocker_master_switch")
                    )
                }

                if (!uiState.permissions.allCorePermissionsGranted) {
                    Text(
                        text = "Grant Usage Access and Display Over Other Apps below before enabling background app locking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accents.statusWarn
                    )
                }
            }
        }

        // Permissions Readiness & Transparent Explanation
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianCard)
                    .border(1.dp, accents.cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Required Android Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                PermissionRequirementRow(
                    title = "Usage Access (PACKAGE_USAGE_STATS)",
                    description = "Detects when a selected locked app moves to the foreground.",
                    granted = uiState.permissions.hasUsageStatsAccess,
                    buttonTestTag = "applocker_grant_usage",
                    onGrantClick = {
                        sessionManager.allowTemporaryExternalActivity()
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                PermissionRequirementRow(
                    title = "Display Over Other Apps (Overlay)",
                    description = "Allows presenting the PIN lock screen when a locked app launches.",
                    granted = uiState.permissions.hasOverlayPermission,
                    buttonTestTag = "applocker_grant_overlay",
                    onGrantClick = {
                        sessionManager.allowTemporaryExternalActivity()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRequirementRow(
                        title = "Foreground Service Notification",
                        description = "Displays an active protection status notification required by Android.",
                        granted = uiState.permissions.hasNotificationPermission,
                        buttonTestTag = "applocker_grant_notif",
                        onGrantClick = {
                            sessionManager.allowTemporaryExternalActivity()
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianElevated)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accents.neonPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Platform note: Standard Android APIs support PIN-gating app launches via Usage Access + Overlay, and do not allow third-party apps to hide other apps' icons from the system launcher.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Search & Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("applocker_search_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !uiState.showSystemApps,
                        onClick = { if (uiState.showSystemApps) viewModel.toggleShowSystemApps() },
                        label = { Text("User Apps") }
                    )
                    FilterChip(
                        selected = uiState.showSystemApps,
                        onClick = { if (!uiState.showSystemApps) viewModel.toggleShowSystemApps() },
                        label = { Text("Include System Apps") }
                    )
                }
            }
        }

        if (uiState.isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accents.neonPrimary)
                }
            }
        } else if (uiState.apps.isEmpty()) {
            item {
                Text(
                    text = "No matching launchable apps found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(uiState.apps, key = { it.packageName }) { app ->
                val isLocked = uiState.lockedPackageNames.contains(app.packageName)
                InstalledAppRow(
                    app = app,
                    isLocked = isLocked,
                    onToggle = { lock -> viewModel.toggleAppLock(app, lock) }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequirementRow(
    title: String,
    description: String,
    granted: Boolean,
    buttonTestTag: String,
    onGrantClick: () -> Unit
) {
    val accents = LocalVaultAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianElevated.copy(alpha = 0.7f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) accents.statusGood else accents.statusWarn,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (granted) {
            Text(
                text = "Granted",
                style = MaterialTheme.typography.labelMedium,
                color = accents.statusGood
            )
        } else {
            OutlinedButton(
                onClick = onGrantClick,
                modifier = Modifier.testTag(buttonTestTag)
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppItem,
    isLocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accents = LocalVaultAccents.current
    val iconBitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(
                1.dp,
                if (isLocked) accents.neonPrimary.copy(alpha = 0.55f) else accents.cardBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = accents.neonPrimary)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = accents.neonPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Switch(
                checked = isLocked,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("applocker_toggle_${app.packageName}")
            )
        }
    }
}
