package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.VaultThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "hcx_security_prefs")

data class SecuritySettingsState(
    val isPinConfigured: Boolean = false,
    val securityQuestion: String = "",
    val hasRecoveryKey: Boolean = false,
    val themePreset: VaultThemePreset = VaultThemePreset.OBSIDIAN_NEON,
    val autoLockOnBackground: Boolean = true,
    val appLockerEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = false
)

class SecurityPreferencesManager(private val context: Context) {
    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val SECURITY_QUESTION = stringPreferencesKey("security_question")
        val RECOVERY_ANSWER_HASH = stringPreferencesKey("recovery_answer_hash")
        val RECOVERY_ANSWER_SALT = stringPreferencesKey("recovery_answer_salt")
        val RECOVERY_KEY_HASH = stringPreferencesKey("recovery_key_hash")
        val RECOVERY_KEY_SALT = stringPreferencesKey("recovery_key_salt")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val AUTO_LOCK_ON_BG = booleanPreferencesKey("auto_lock_on_background")
        val APP_LOCKER_ENABLED = booleanPreferencesKey("app_locker_enabled")
        val BIOMETRIC_UNLOCK_ENABLED = booleanPreferencesKey("biometric_unlock_enabled")
    }

    val settingsFlow: Flow<SecuritySettingsState> = context.securityDataStore.data.map { prefs ->
        val themeId = prefs[Keys.THEME_PRESET] ?: VaultThemePreset.OBSIDIAN_NEON.id
        val preset = VaultThemePreset.entries.firstOrNull { it.id == themeId }
            ?: VaultThemePreset.OBSIDIAN_NEON
        SecuritySettingsState(
            isPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            securityQuestion = prefs[Keys.SECURITY_QUESTION] ?: "",
            hasRecoveryKey = !prefs[Keys.RECOVERY_KEY_HASH].isNullOrBlank(),
            themePreset = preset,
            autoLockOnBackground = prefs[Keys.AUTO_LOCK_ON_BG] ?: true,
            appLockerEnabled = prefs[Keys.APP_LOCKER_ENABLED] ?: false,
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] ?: false
        )
    }

    suspend fun setupInitialSecurity(
        pin: String,
        securityQuestion: String,
        securityAnswer: String
    ): String {
        val hashedPin = PinHashHelper.hashSecret(pin)
        val normalizedAnswer = securityAnswer.trim().lowercase()
        val hashedAnswer = PinHashHelper.hashSecret(normalizedAnswer)
        val recoveryKey = PinHashHelper.generateRecoveryKey()
        val hashedRecoveryKey = PinHashHelper.hashSecret(PinHashHelper.normalizeRecoveryInput(recoveryKey))

        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashedPin.hashBase64
            prefs[Keys.PIN_SALT] = hashedPin.saltBase64
            prefs[Keys.SECURITY_QUESTION] = securityQuestion.trim()
            prefs[Keys.RECOVERY_ANSWER_HASH] = hashedAnswer.hashBase64
            prefs[Keys.RECOVERY_ANSWER_SALT] = hashedAnswer.saltBase64
            prefs[Keys.RECOVERY_KEY_HASH] = hashedRecoveryKey.hashBase64
            prefs[Keys.RECOVERY_KEY_SALT] = hashedRecoveryKey.saltBase64
        }
        return recoveryKey
    }

    suspend fun verifyPin(candidatePin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        return PinHashHelper.verifySecret(
            candidate = candidatePin,
            storedHashBase64 = prefs[Keys.PIN_HASH],
            storedSaltBase64 = prefs[Keys.PIN_SALT]
        )
    }

    suspend fun updatePin(newPin: String) {
        val hashedPin = PinHashHelper.hashSecret(newPin)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashedPin.hashBase64
            prefs[Keys.PIN_SALT] = hashedPin.saltBase64
        }
    }

    suspend fun updateRecoveryQuestionAndAnswer(question: String, answer: String) {
        val hashedAnswer = PinHashHelper.hashSecret(answer.trim().lowercase())
        context.securityDataStore.edit { prefs ->
            prefs[Keys.SECURITY_QUESTION] = question.trim()
            prefs[Keys.RECOVERY_ANSWER_HASH] = hashedAnswer.hashBase64
            prefs[Keys.RECOVERY_ANSWER_SALT] = hashedAnswer.saltBase64
        }
    }

    suspend fun regenerateRecoveryKey(): String {
        val recoveryKey = PinHashHelper.generateRecoveryKey()
        val hashed = PinHashHelper.hashSecret(PinHashHelper.normalizeRecoveryInput(recoveryKey))
        context.securityDataStore.edit { prefs ->
            prefs[Keys.RECOVERY_KEY_HASH] = hashed.hashBase64
            prefs[Keys.RECOVERY_KEY_SALT] = hashed.saltBase64
        }
        return recoveryKey
    }

    suspend fun verifyRecoveryAnswer(answer: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        return PinHashHelper.verifySecret(
            candidate = answer.trim().lowercase(),
            storedHashBase64 = prefs[Keys.RECOVERY_ANSWER_HASH],
            storedSaltBase64 = prefs[Keys.RECOVERY_ANSWER_SALT]
        )
    }

    suspend fun verifyRecoveryKey(keyInput: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        return PinHashHelper.verifySecret(
            candidate = PinHashHelper.normalizeRecoveryInput(keyInput),
            storedHashBase64 = prefs[Keys.RECOVERY_KEY_HASH],
            storedSaltBase64 = prefs[Keys.RECOVERY_KEY_SALT]
        )
    }

    suspend fun setThemePreset(preset: VaultThemePreset) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.THEME_PRESET] = preset.id
        }
    }

    suspend fun setAutoLockOnBackground(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_ON_BG] = enabled
        }
    }

    suspend fun setAppLockerEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.APP_LOCKER_ENABLED] = enabled
        }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] = enabled
        }
    }
}
