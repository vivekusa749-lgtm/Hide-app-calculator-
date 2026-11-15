package com.example.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BackupRepository
import com.example.security.KeystoreCryptoManager
import com.example.security.SecurityPreferencesManager
import com.example.security.SecuritySettingsState
import com.example.ui.theme.VaultThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsFeedbackState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val newlyGeneratedRecoveryKey: String? = null
)

class SettingsViewModel(
    private val securityPrefs: SecurityPreferencesManager,
    private val backupRepository: BackupRepository,
    cryptoManager: KeystoreCryptoManager
) : ViewModel() {

    val settings: StateFlow<SecuritySettingsState> = securityPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecuritySettingsState())

    val isKeystoreHardwareReady: Boolean = cryptoManager.isKeystoreReady()

    private val _feedback = MutableStateFlow(SettingsFeedbackState())
    val feedback: StateFlow<SettingsFeedbackState> = _feedback.asStateFlow()

    fun changePin(currentPin: String, newPin: String, confirmPin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (!securityPrefs.verifyPin(currentPin)) {
                _feedback.update { it.copy(message = "Current PIN is incorrect.", isError = true) }
                return@launch
            }
            if (newPin.length !in 4..8 || !newPin.all { it.isDigit() }) {
                _feedback.update { it.copy(message = "New PIN must be 4 to 8 digits.", isError = true) }
                return@launch
            }
            if (newPin != confirmPin) {
                _feedback.update { it.copy(message = "New PINs do not match.", isError = true) }
                return@launch
            }
            securityPrefs.updatePin(newPin)
            _feedback.update { it.copy(message = "Secret PIN updated successfully.", isError = false) }
            onSuccess()
        }
    }

    fun selectTheme(preset: VaultThemePreset) {
        viewModelScope.launch {
            securityPrefs.setThemePreset(preset)
        }
    }

    fun setAutoLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            securityPrefs.setAutoLockOnBackground(enabled)
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPrefs.setBiometricUnlockEnabled(enabled)
        }
    }

    fun updateRecoveryQuestion(question: String, answer: String, onSuccess: () -> Unit) {
        if (question.trim().length < 4 || answer.trim().length < 2) {
            _feedback.update {
                it.copy(message = "Provide a valid security question and answer.", isError = true)
            }
            return
        }
        viewModelScope.launch {
            securityPrefs.updateRecoveryQuestionAndAnswer(question, answer)
            _feedback.update { it.copy(message = "Recovery question updated.", isError = false) }
            onSuccess()
        }
    }

    fun regenerateRecoveryKey() {
        viewModelScope.launch {
            val key = securityPrefs.regenerateRecoveryKey()
            _feedback.update {
                it.copy(
                    newlyGeneratedRecoveryKey = key,
                    message = "New offline recovery key generated.",
                    isError = false
                )
            }
        }
    }

    fun dismissGeneratedRecoveryKey() {
        _feedback.update { it.copy(newlyGeneratedRecoveryKey = null) }
    }

    fun exportBackup(destUri: Uri, passphrase: String) {
        viewModelScope.launch {
            _feedback.update { it.copy(isBusy = true, message = null, isError = false) }
            val res = runCatching { backupRepository.exportEncryptedBackup(destUri, passphrase) }
            res.onSuccess { count ->
                _feedback.update {
                    it.copy(
                        isBusy = false,
                        message = "Exported encrypted backup ($count media items).",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _feedback.update {
                    it.copy(
                        isBusy = false,
                        message = err.message ?: "Failed to export encrypted backup.",
                        isError = true
                    )
                }
            }
        }
    }

    fun importBackup(sourceUri: Uri, passphrase: String) {
        viewModelScope.launch {
            _feedback.update { it.copy(isBusy = true, message = null, isError = false) }
            val res = runCatching { backupRepository.importEncryptedBackup(sourceUri, passphrase) }
            res.onSuccess { count ->
                _feedback.update {
                    it.copy(
                        isBusy = false,
                        message = "Restored $count item(s) into Keystore vault.",
                        isError = false
                    )
                }
            }.onFailure { err ->
                _feedback.update {
                    it.copy(
                        isBusy = false,
                        message = err.message ?: "Failed to restore backup.",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _feedback.update { it.copy(message = null) }
    }

    class Factory(
        private val securityPrefs: SecurityPreferencesManager,
        private val backupRepository: BackupRepository,
        private val cryptoManager: KeystoreCryptoManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(securityPrefs, backupRepository, cryptoManager) as T
    }
}
