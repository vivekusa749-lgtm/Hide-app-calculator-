package com.example.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.VaultSessionManager
import com.example.ui.applocker.AppLockerScreen
import com.example.ui.applocker.AppLockerViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.navigation.VaultTabRoute
import com.example.ui.photos.PhotoVaultScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.videos.VideoVaultScreen

private data class VaultNavItem(
    val route: VaultTabRoute,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val vaultNavItems = listOf(
    VaultNavItem(VaultTabRoute.Dashboard, "Vault", Icons.Default.Dashboard, "nav_dashboard"),
    VaultNavItem(VaultTabRoute.Photos, "Photos", Icons.Default.PhotoLibrary, "nav_photos"),
    VaultNavItem(VaultTabRoute.Videos, "Videos", Icons.Default.VideoLibrary, "nav_videos"),
    VaultNavItem(VaultTabRoute.AppLocker, "Locker", Icons.Default.AppBlocking, "nav_applocker"),
    VaultNavItem(VaultTabRoute.Settings, "Settings", Icons.Default.Settings, "nav_settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScaffold(
    mediaViewModel: MediaVaultViewModel,
    appLockerViewModel: AppLockerViewModel,
    settingsViewModel: SettingsViewModel,
    sessionManager: VaultSessionManager,
    onLockVault: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val accents = LocalVaultAccents.current

    val storageSummary by mediaViewModel.storageSummary.collectAsStateWithLifecycle()
    val appLockerState by appLockerViewModel.uiState.collectAsStateWithLifecycle()

    val currentTitle = when {
        currentDest?.hasRoute<VaultTabRoute.Photos>() == true -> "Photo Vault"
        currentDest?.hasRoute<VaultTabRoute.Videos>() == true -> "Video Vault"
        currentDest?.hasRoute<VaultTabRoute.AppLocker>() == true -> "App Locker"
        currentDest?.hasRoute<VaultTabRoute.Settings>() == true -> "Vault Settings"
        else -> "Private Vault"
    }

    val navigateToTab: (VaultTabRoute) -> Unit = { target ->
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Pressing system Back on Dashboard locks back to the Calculator
    BackHandler(enabled = currentDest?.hasRoute<VaultTabRoute.Dashboard>() == true) {
        onLockVault()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBlack,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianSurface,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onLockVault,
                            modifier = Modifier.testTag("topbar_lock_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock vault",
                                tint = accents.neonPrimary
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = ObsidianSurface) {
                        vaultNavItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                },
                                label = {
                                    Text(text = item.label, maxLines = 1)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accents.neonPrimary,
                                    selectedTextColor = accents.neonPrimary,
                                    indicatorColor = ObsidianCard,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        vaultNavItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = accents.neonPrimary,
                                    selectedTextColor = accents.neonPrimary,
                                    indicatorColor = ObsidianCard,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = VaultTabRoute.Dashboard
                    ) {
                        composable<VaultTabRoute.Dashboard> {
                            DashboardScreen(
                                storageSummary = storageSummary,
                                lockedAppsCount = appLockerState.lockedPackageNames.size,
                                isAppLockerActive = appLockerState.isProtectionEnabled,
                                isKeystoreReady = settingsViewModel.isKeystoreHardwareReady,
                                onNavigateTab = navigateToTab,
                                onLockVault = onLockVault
                            )
                        }
                        composable<VaultTabRoute.Photos> {
                            PhotoVaultScreen(
                                viewModel = mediaViewModel,
                                sessionManager = sessionManager
                            )
                        }
                        composable<VaultTabRoute.Videos> {
                            VideoVaultScreen(
                                viewModel = mediaViewModel,
                                sessionManager = sessionManager
                            )
                        }
                        composable<VaultTabRoute.AppLocker> {
                            AppLockerScreen(
                                viewModel = appLockerViewModel,
                                sessionManager = sessionManager
                            )
                        }
                        composable<VaultTabRoute.Settings> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                sessionManager = sessionManager
                            )
                        }
                    }
                }
            }
        }
    }
}
