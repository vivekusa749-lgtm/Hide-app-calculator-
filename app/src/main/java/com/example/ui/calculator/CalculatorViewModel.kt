package com.example.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calculator.CalcOperator
import com.example.calculator.CalculatorEngine
import com.example.calculator.CalculatorState
import com.example.security.SecurityPreferencesManager
import com.example.security.SecuritySettingsState
import com.example.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecoveryMethod {
    SECURITY_QUESTION,
    RECOVERY_KEY
}

data class CalculatorUiState(
    val calc: CalculatorState = CalculatorState(),
    val showRecoveryDialog: Boolean = false,
    val recoveryStepVerified: Boolean = false,
    val recoveryMethod: RecoveryMethod = RecoveryMethod.SECURITY_QUESTION,
    val recoveryError: String? = null,
    val generatedRecoveryKeyBanner: String? = null,
    val setupError: String? = null
)

class CalculatorViewModel(
    private val securityPrefs: SecurityPreferencesManager,
    private val sessionManager: VaultSessionManager,
    private val engine: CalculatorEngine = CalculatorEngine()
) : ViewModel() {

    val securitySettings: StateFlow<SecuritySettingsState> = securityPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecuritySettingsState())

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        _uiState.update { it.copy(calc = engine.inputDigit(it.calc, digit)) }
    }

    fun onDecimal() {
        _uiState.update { it.copy(calc = engine.inputDecimal(it.calc)) }
    }

    fun onOperator(operator: CalcOperator) {
        _uiState.update { it.copy(calc = engine.applyOperator(it.calc, operator)) }
    }

    fun onPercent() {
        _uiState.update { it.copy(calc = engine.applyPercent(it.calc)) }
    }

    fun onToggleSign() {
        _uiState.update { it.copy(calc = engine.toggleSign(it.calc)) }
    }

    fun onBackspace() {
        _uiState.update { it.copy(calc = engine.backspace(it.calc)) }
    }

    fun onClear() {
        _uiState.update { it.copy(calc = engine.clearAll()) }
    }

    fun onEquals() {
        val currentCalc = _uiState.value.calc
        if (currentCalc.isRawPinCandidate && currentCalc.displayText.length in 4..8) {
            val candidate = currentCalc.displayText
            viewModelScope.launch {
                if (securityPrefs.verifyPin(candidate)) {
                    _uiState.update { it.copy(calc = engine.clearAll()) }
                    sessionManager.unlockVault()
                } else {
                    _uiState.update { it.copy(calc = engine.evaluateEquals(currentCalc)) }
                }
            }
        } else {
            _uiState.update { it.copy(calc = engine.evaluateEquals(currentCalc)) }
        }
    }

    fun unlockViaBiometric() {
        _uiState.update { it.copy(calc = engine.clearAll()) }
        sessionManager.unlockVault()
    }

    fun completeInitialSetup(pin: String, confirmPin: String, question: String, answer: String) {
        val error = when {
            pin.length !in 4..8 || !pin.all { it.isDigit() } -> "PIN must be 4 to 8 digits."
            pin != confirmPin -> "PINs do not match."
            question.trim().length < 4 -> "Enter a security question for recovery."
            answer.trim().length < 2 -> "Enter a recovery answer."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(setupError = error) }
            return
        }
        viewModelScope.launch {
            val recoveryKey = securityPrefs.setupInitialSecurity(pin, question, answer)
            _uiState.update {
                it.copy(
                    setupError = null,
                    generatedRecoveryKeyBanner = recoveryKey,
                    calc = engine.clearAll()
                )
            }
        }
    }

    fun dismissRecoveryKeyBanner() {
        _uiState.update { it.copy(generatedRecoveryKeyBanner = null) }
    }

    fun openForgotPinDialog() {
        _uiState.update {
            it.copy(
                showRecoveryDialog = true,
                recoveryStepVerified = false,
                recoveryError = null
            )
        }
    }

    fun closeForgotPinDialog() {
        _uiState.update {
            it.copy(
                showRecoveryDialog = false,
                recoveryStepVerified = false,
                recoveryError = null
            )
        }
    }

    fun setRecoveryMethod(method: RecoveryMethod) {
        _uiState.update { it.copy(recoveryMethod = method, recoveryError = null) }
    }

    fun verifyRecoveryInput(input: String) {
        viewModelScope.launch {
            val verified = when (_uiState.value.recoveryMethod) {
                RecoveryMethod.SECURITY_QUESTION -> securityPrefs.verifyRecoveryAnswer(input)
                RecoveryMethod.RECOVERY_KEY -> securityPrefs.verifyRecoveryKey(input)
            }
            if (verified) {
                _uiState.update { it.copy(recoveryStepVerified = true, recoveryError = null) }
            } else {
                _uiState.update {
                    it.copy(
                        recoveryError = if (_uiState.value.recoveryMethod == RecoveryMethod.SECURITY_QUESTION) {
                            "Incorrect security answer."
                        } else {
                            "Invalid recovery key."
                        }
                    )
                }
            }
        }
    }

    fun resetPinAfterRecovery(newPin: String, confirmPin: String) {
        val error = when {
            newPin.length !in 4..8 || !newPin.all { it.isDigit() } -> "New PIN must be 4 to 8 digits."
            newPin != confirmPin -> "PINs do not match."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(recoveryError = error) }
            return
        }
        viewModelScope.launch {
            securityPrefs.updatePin(newPin)
            _uiState.update {
                it.copy(
                    showRecoveryDialog = false,
                    recoveryStepVerified = false,
                    recoveryError = null,
                    calc = engine.clearAll()
                )
            }
            sessionManager.unlockVault()
        }
    }

    class Factory(
        private val securityPrefs: SecurityPreferencesManager,
        private val sessionManager: VaultSessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CalculatorViewModel(securityPrefs, sessionManager) as T
    }
}
