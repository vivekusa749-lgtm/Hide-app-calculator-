package com.example.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.VaultStorageSummary
import com.example.ui.common.formatFileSize
import com.example.ui.navigation.VaultTabRoute
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    storageSummary: VaultStorageSummary,
    lockedAppsCount: Int,
    isAppLockerActive: Boolean,
    isKeystoreReady: Boolean,
    onNavigateTab: (VaultTabRoute) -> Unit,
    onLockVault: () -> Unit
) {
    val accents = LocalVaultAccents.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Shield Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, accents.cardBorder, RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_hero_banner),
                    contentDescription = "Encrypted vault core banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ObsidianBlack.copy(alpha = 0.90f),
                                    ObsidianBlack.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ObsidianElevated.copy(alpha = 0.85f))
                            .border(1.dp, accents.neonPrimary.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isKeystoreReady) accents.statusGood else accents.statusWarn)
                        )
                        Text(
                            text = if (isKeystoreReady) "KEYSTORE AES-256 ARMED" else "LOCAL VAULT READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.neonPrimary
                        )
                    }

                    Column {
                        Text(
                            text = "Hide Calculator X",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "100% offline encrypted storage · Zero server sync",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Quick Bento Grid (Photos, Videos, App Locker, Settings)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultBentoTile(
                        title = "Photo Vault",
                        subtitle = "${storageSummary.photoCount} encrypted",
                        metric = formatFileSize(storageSummary.photoBytes),
                        icon = Icons.Default.PhotoLibrary,
                        accentColor = accents.neonPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_photos_tile"),
                        onClick = { onNavigateTab(VaultTabRoute.Photos) }
                    )
                    VaultBentoTile(
                        title = "Video Vault",
                        subtitle = "${storageSummary.videoCount} encrypted",
                        metric = formatFileSize(storageSummary.videoBytes),
                        icon = Icons.Default.VideoLibrary,
                        accentColor = accents.neonSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_videos_tile"),
                        onClick = { onNavigateTab(VaultTabRoute.Videos) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultBentoTile(
                        title = "App Locker",
                        subtitle = if (isAppLockerActive) "Protection Active" else "Standby",
                        metric = "$lockedAppsCount locked",
                        icon = Icons.Default.AppBlocking,
                        accentColor = if (isAppLockerActive) accents.statusGood else accents.neonPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_applocker_tile"),
                        onClick = { onNavigateTab(VaultTabRoute.AppLocker) }
                    )
                    VaultBentoTile(
                        title = "Settings",
                        subtitle = "PIN, Theme & Backup",
                        metric = "Security",
                        icon = Icons.Default.Settings,
                        accentColor = accents.neonSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_settings_tile"),
                        onClick = { onNavigateTab(VaultTabRoute.Settings) }
                    )
                }
            }
        }

        // Storage Information Card
        item {
            StorageInfoCard(storageSummary = storageSummary)
        }

        // Quick Lock Action Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianCard)
                    .border(1.dp, accents.cardBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onLockVault)
                    .padding(16.dp)
                    .testTag("dashboard_quick_lock_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accents.neonPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Instant lock",
                            tint = accents.neonPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "Instant Disguise Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Lock vault and return to calculator immediately",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = accents.neonSecondary
                )
            }
        }
    }
}

@Composable
private fun VaultBentoTile(
    title: String,
    subtitle: String,
    metric: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accents = LocalVaultAccents.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        ObsidianCard,
                        ObsidianElevated.copy(alpha = 0.65f)
                    )
                )
            )
            .border(1.dp, accents.cardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor
                )
            }
            Text(
                text = metric,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = accentColor
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StorageInfoCard(storageSummary: VaultStorageSummary) {
    val accents = LocalVaultAccents.current
    val totalDevice = storageSummary.deviceTotalBytes.coerceAtLeast(1L)
    val deviceUsed = (totalDevice - storageSummary.deviceFreeBytes).coerceAtLeast(0L)
    val vaultTotal = storageSummary.totalVaultBytes.coerceAtLeast(0L)

    val photoRatio = (storageSummary.photoBytes.toFloat() / totalDevice.toFloat()).coerceIn(0f, 1f)
    val videoRatio = (storageSummary.videoBytes.toFloat() / totalDevice.toFloat()).coerceIn(0f, 1f)
    val otherRatio = ((deviceUsed - vaultTotal).coerceAtLeast(0L).toFloat() / totalDevice.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(ObsidianCard)
            .border(1.dp, accents.cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = "Storage telemetry",
                    tint = accents.neonPrimary
                )
                Text(
                    text = "Storage Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Text(
                text = "${formatFileSize(storageSummary.deviceFreeBytes)} free",
                style = MaterialTheme.typography.labelMedium,
                color = accents.statusGood
            )
        }

        // Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(ObsidianElevated)
        ) {
            if (photoRatio > 0.004f) {
                Box(
                    modifier = Modifier
                        .weight(photoRatio)
                        .fillMaxSize()
                        .background(accents.neonPrimary)
                )
            }
            if (videoRatio > 0.004f) {
                Box(
                    modifier = Modifier
                        .weight(videoRatio)
                        .fillMaxSize()
                        .background(accents.neonSecondary)
                )
            }
            if (otherRatio > 0.004f) {
                Box(
                    modifier = Modifier
                        .weight(otherRatio)
                        .fillMaxSize()
                        .background(Color(0xFF334155))
                )
            }
            val freeRatio = (1f - (photoRatio + videoRatio + otherRatio)).coerceAtLeast(0.01f)
            Spacer(modifier = Modifier.weight(freeRatio))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StorageLegendDot(
                color = accents.neonPrimary,
                label = "Photos",
                value = formatFileSize(storageSummary.photoBytes)
            )
            StorageLegendDot(
                color = accents.neonSecondary,
                label = "Videos",
                value = formatFileSize(storageSummary.videoBytes)
            )
            StorageLegendDot(
                color = Color(0xFF64748B),
                label = "Vault Total",
                value = formatFileSize(vaultTotal)
            )
        }
    }
}

@Composable
private fun StorageLegendDot(color: Color, label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}
