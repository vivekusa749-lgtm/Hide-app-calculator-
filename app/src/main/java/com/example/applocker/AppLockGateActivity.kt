package com.example.applocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HideCalcApplication
import com.example.security.BiometricAuthHelper
import com.example.ui.theme.HideCalcTheme
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class AppLockGateActivity : FragmentActivity() {
    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateHome()
            }
        })

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(targetPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(targetPackage)

        val container = (application as HideCalcApplication).container

        setContent {
            val settings by container.securityPrefs.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.security.SecuritySettingsState()
            )
            val biometricAvailable = remember { BiometricAuthHelper.isBiometricAvailable(this) }
            val unlockTarget: () -> Unit = {
                if (targetPackage.isNotEmpty()) {
                    AppLockerSessionBridge.markUnlocked(targetPackage)
                }
                finish()
            }

            LaunchedEffect(settings.biometricUnlockEnabled) {
                if (settings.biometricUnlockEnabled && biometricAvailable) {
                    BiometricAuthHelper.authenticate(
                        activity = this@AppLockGateActivity,
                        title = "Unlock $appLabel",
                        subtitle = "Verify fingerprint or enter PIN",
                        onSuccess = unlockTarget
                    )
                }
            }

            HideCalcTheme(themePreset = settings.themePreset) {
                AppLockGateScreen(
                    lockedAppName = appLabel,
                    showBiometricOption = settings.biometricUnlockEnabled && biometricAvailable,
                    onBiometricClick = {
                        BiometricAuthHelper.authenticate(
                            activity = this@AppLockGateActivity,
                            title = "Unlock $appLabel",
                            subtitle = "Verify fingerprint or enter PIN",
                            onSuccess = unlockTarget
                        )
                    },
                    onVerifyPin = { pin -> container.securityPrefs.verifyPin(pin) },
                    onUnlocked = unlockTarget,
                    onCancelToHome = { navigateHome() }
                )
            }
        }
    }

    private fun navigateHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
private fun AppLockGateScreen(
    lockedAppName: String,
    showBiometricOption: Boolean,
    onBiometricClick: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancelToHome: () -> Unit
) {
    val accents = LocalVaultAccents.current
    val scope = rememberCoroutineScope()
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accents.neonPrimary.copy(alpha = 0.22f),
                                    accents.neonSecondary.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(1.5.dp, accents.neonPrimary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked app shield",
                        tint = accents.neonPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "$lockedAppName is Locked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter your Hide Calculator X PIN to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotsCount = maxOf(4, pinInput.length.coerceAtMost(8))
                    repeat(dotsCount) { idx ->
                        val filled = idx < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (filled) accents.neonPrimary else ObsidianCard)
                                .border(
                                    1.dp,
                                    if (filled) accents.neonPrimary else accents.cardBorder,
                                    CircleShape
                                )
                        )
                    }
                }
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("DEL", "0", "OK")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { key ->
                            val isAction = key == "OK"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isAction) {
                                            Brush.linearGradient(listOf(accents.neonPrimary, accents.neonSecondary))
                                        } else {
                                            Brush.linearGradient(listOf(accents.keycapBg, accents.keycapBg))
                                        }
                                    )
                                    .border(1.dp, accents.cardBorder, RoundedCornerShape(20.dp))
                                    .clickable {
                                        errorText = null
                                        when (key) {
                                            "DEL" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                            "OK" -> {
                                                if (pinInput.length < 4) {
                                                    errorText = "Enter at least 4 digits"
                                                } else {
                                                    scope.launch {
                                                        if (onVerifyPin(pinInput)) {
                                                            onUnlocked()
                                                        } else {
                                                            errorText = "Incorrect PIN"
                                                            pinInput = ""
                                                        }
                                                    }
                                                }
                                            }
                                            else -> if (pinInput.length < 8) pinInput += key
                                        }
                                    }
                                    .testTag("applock_gate_key_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete digit",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (isAction) ObsidianBlack else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (showBiometricOption) {
                        TextButton(
                            onClick = onBiometricClick,
                            modifier = Modifier.testTag("applock_gate_biometric_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Unlock with fingerprint",
                                tint = accents.neonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Fingerprint", color = accents.neonPrimary)
                        }
                    }
                    TextButton(onClick = onCancelToHome) {
                        Text("Exit to Home", color = TextSecondary)
                    }
                }
            }
        }
    }
}
