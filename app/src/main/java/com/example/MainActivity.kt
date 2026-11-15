package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.SecuritySettingsState
import com.example.ui.applocker.AppLockerViewModel
import com.example.ui.calculator.CalculatorScreen
import com.example.ui.calculator.CalculatorViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.HideCalcTheme
import com.example.ui.vault.MediaVaultViewModel
import com.example.ui.vault.VaultScaffold

class MainActivity : FragmentActivity() {
    private var autoLockEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as HideCalcApplication).container

        setContent {
            val settings by container.securityPrefs.settingsFlow.collectAsStateWithLifecycle(
                initialValue = SecuritySettingsState()
            )
            val isVaultUnlocked by container.sessionManager.isVaultUnlocked.collectAsStateWithLifecycle()

            LaunchedEffect(settings.autoLockOnBackground) {
                autoLockEnabled = settings.autoLockOnBackground
            }

            // Prevent screenshots & Recents thumbnail previews while inside the private vault
            LaunchedEffect(isVaultUnlocked) {
                if (isVaultUnlocked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            HideCalcTheme(themePreset = settings.themePreset) {
                Crossfade(
                    targetState = isVaultUnlocked,
                    animationSpec = tween(durationMillis = 260),
                    label = "vault_disguise_transition"
                ) { unlocked ->
                    if (!unlocked) {
                        val calcViewModel: CalculatorViewModel = viewModel(
                            factory = CalculatorViewModel.Factory(
                                securityPrefs = container.securityPrefs,
                                sessionManager = container.sessionManager
                            )
                        )
                        CalculatorScreen(viewModel = calcViewModel)
                    } else {
                        val mediaViewModel: MediaVaultViewModel = viewModel(
                            factory = MediaVaultViewModel.Factory(
                                repository = container.mediaRepository,
                                cryptoManager = container.cryptoManager
                            )
                        )
                        val appLockerViewModel: AppLockerViewModel = viewModel(
                            factory = AppLockerViewModel.Factory(
                                appLockerRepository = container.appLockerRepository,
                                securityPrefs = container.securityPrefs
                            )
                        )
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                securityPrefs = container.securityPrefs,
                                backupRepository = container.backupRepository,
                                cryptoManager = container.cryptoManager
                            )
                        )
                        VaultScaffold(
                            mediaViewModel = mediaViewModel,
                            appLockerViewModel = appLockerViewModel,
                            settingsViewModel = settingsViewModel,
                            sessionManager = container.sessionManager,
                            onLockVault = { container.sessionManager.lockVault() }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val container = (application as HideCalcApplication).container
        if (autoLockEnabled && !isChangingConfigurations && container.sessionManager.consumeOrShouldAutoLock()) {
            container.sessionManager.lockVault()
        }
    }
}
