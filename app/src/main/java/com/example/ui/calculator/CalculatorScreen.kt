package com.example.ui.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.calculator.CalcOperator
import com.example.security.BiometricAuthHelper
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val accents = LocalVaultAccents.current
    val haptic = LocalHapticFeedback.current
    val activity = LocalContext.current as? FragmentActivity

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBlack
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ObsidianBlack,
                            Color(0xFF070A12),
                            ObsidianBlack
                        )
                    )
                )
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            val isWide = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .widthIn(max = if (isWide) 460.dp else 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Discreet Top Bar & Expression Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextTertiary
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onBackspace()
                            },
                            modifier = Modifier.testTag("calc_backspace_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = uiState.calc.expressionText.ifEmpty { " " },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Normal
                        ),
                        color = accents.neonPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayFontSize = when {
                        uiState.calc.displayText.length > 13 -> 34.sp
                        uiState.calc.displayText.length > 9 -> 44.sp
                        else -> 56.sp
                    }

                    Text(
                        text = uiState.calc.displayText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = displayFontSize,
                            lineHeight = displayFontSize * 1.15f
                        ),
                        color = if (uiState.calc.hasError) MaterialTheme.colorScheme.error else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 12.dp)
                            .testTag("calc_display")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5x4 Modern Tactile Keypad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val rowHeight = if (isWide) 68.dp else 74.dp

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CalcKeyButton(
                            label = "AC",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_ac"),
                            backgroundColor = accents.keycapOperatorBg,
                            contentColor = accents.neonSecondary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onClear()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openForgotPinDialog()
                            }
                        )
                        CalcKeyButton(
                            label = "+/-",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_sign"),
                            backgroundColor = accents.keycapOperatorBg,
                            contentColor = TextPrimary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onToggleSign()
                            }
                        )
                        CalcKeyButton(
                            label = "%",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_percent"),
                            backgroundColor = accents.keycapOperatorBg,
                            contentColor = TextPrimary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onPercent()
                            }
                        )
                        CalcKeyButton(
                            label = "÷",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_divide"),
                            backgroundColor = accents.keycapOperatorBg,
                            contentColor = accents.neonPrimary,
                            borderColor = if (uiState.calc.pendingOperator == CalcOperator.DIVIDE) {
                                accents.neonPrimary
                            } else {
                                accents.cardBorder
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onOperator(CalcOperator.DIVIDE)
                            }
                        )
                    }

                    val digitRows = listOf(
                        listOf('7', '8', '9') to CalcOperator.MULTIPLY,
                        listOf('4', '5', '6') to CalcOperator.SUBTRACT,
                        listOf('1', '2', '3') to CalcOperator.ADD
                    )

                    digitRows.forEach { (digits, op) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            digits.forEach { digit ->
                                CalcKeyButton(
                                    label = digit.toString(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(rowHeight)
                                        .testTag("calc_key_$digit"),
                                    backgroundColor = accents.keycapBg,
                                    contentColor = TextPrimary,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onDigit(digit)
                                    }
                                )
                            }
                            CalcKeyButton(
                                label = op.symbol,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(rowHeight)
                                    .testTag("calc_key_${op.name.lowercase()}"),
                                backgroundColor = accents.keycapOperatorBg,
                                contentColor = accents.neonPrimary,
                                borderColor = if (uiState.calc.pendingOperator == op) {
                                    accents.neonPrimary
                                } else {
                                    accents.cardBorder
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onOperator(op)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CalcKeyButton(
                            label = "0",
                            modifier = Modifier
                                .weight(2f)
                                .height(rowHeight)
                                .testTag("calc_key_0"),
                            backgroundColor = accents.keycapBg,
                            contentColor = TextPrimary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onDigit('0')
                            }
                        )
                        CalcKeyButton(
                            label = ".",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_decimal"),
                            backgroundColor = accents.keycapBg,
                            contentColor = TextPrimary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onDecimal()
                            }
                        )
                        CalcKeyButton(
                            label = "=",
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .testTag("calc_key_equals"),
                            gradientBrush = Brush.linearGradient(
                                listOf(accents.calcEqualStart, accents.calcEqualEnd)
                            ),
                            contentColor = ObsidianBlack,
                            borderColor = Color.Transparent,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onEquals()
                            },
                            onLongClick = if (settings.biometricUnlockEnabled && activity != null) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = "Unlock Hide Calculator X",
                                        subtitle = "Verify fingerprint to open private vault",
                                        onSuccess = { viewModel.unlockViaBiometric() }
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            // First-launch PIN & Recovery onboarding dialog
            AnimatedVisibility(
                visible = !settings.isPinConfigured,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                InitialPinSetupDialog(
                    errorText = uiState.setupError,
                    onCompleteSetup = viewModel::completeInitialSetup
                )
            }

            // One-time Recovery Key reveal banner after setup
            uiState.generatedRecoveryKeyBanner?.let { recoveryKey ->
                RecoveryKeyRevealDialog(
                    recoveryKey = recoveryKey,
                    onDismiss = viewModel::dismissRecoveryKeyBanner
                )
            }

            // Forgot PIN recovery flow (triggered via long-press on AC)
            if (uiState.showRecoveryDialog) {
                ForgotPinRecoveryDialog(
                    securityQuestion = settings.securityQuestion,
                    uiState = uiState,
                    onDismiss = viewModel::closeForgotPinDialog,
                    onMethodChange = viewModel::setRecoveryMethod,
                    onVerifyRecovery = viewModel::verifyRecoveryInput,
                    onResetPin = viewModel::resetPinAfterRecovery
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalcKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ObsidianCard,
    gradientBrush: Brush? = null,
    contentColor: Color = TextPrimary,
    borderColor: Color = LocalVaultAccents.current.cardBorder,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "key_scale"
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (gradientBrush != null) Modifier.background(gradientBrush)
                else Modifier.background(backgroundColor)
            )
            .border(1.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (label.length > 2) 18.sp else 24.sp
            ),
            color = contentColor
        )
    }
}

@Composable
private fun InitialPinSetupDialog(
    errorText: String?,
    onCompleteSetup: (pin: String, confirmPin: String, question: String, answer: String) -> Unit
) {
    val accents = LocalVaultAccents.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("What is the name of your first pet?") }
    var answer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        containerColor = ObsidianCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security setup",
                    tint = accents.neonPrimary
                )
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Secret PIN (4-8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_confirm_pin_input")
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Recovery Question") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_question_input")
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Recovery Answer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_answer_input")
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(R.string.recovery_hint_tip),
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.neonPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCompleteSetup(pin, confirmPin, question, answer) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.neonPrimary,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier.testTag("setup_save_button")
            ) {
                Text("Arm Vault")
            }
        }
    )
}

@Composable
private fun RecoveryKeyRevealDialog(
    recoveryKey: String,
    onDismiss: () -> Unit
) {
    val accents = LocalVaultAccents.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Recovery Key",
                    tint = accents.neonSecondary
                )
                Text("Save Your Recovery Key", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Store this offline key somewhere safe. If you ever forget your PIN, long-press AC on the calculator and enter this key or answer your security question.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianElevated)
                        .border(1.dp, accents.neonPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recoveryKey,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = accents.neonPrimary
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(recoveryKey))
                        copied = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy recovery key",
                            tint = TextPrimary
                        )
                    }
                }
                if (copied) {
                    Text(
                        text = "Copied to clipboard.",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.statusGood
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.neonPrimary,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier.testTag("recovery_key_saved_button")
            ) {
                Text("I've Saved It")
            }
        }
    )
}

@Composable
private fun ForgotPinRecoveryDialog(
    securityQuestion: String,
    uiState: CalculatorUiState,
    onDismiss: () -> Unit,
    onMethodChange: (RecoveryMethod) -> Unit,
    onVerifyRecovery: (String) -> Unit,
    onResetPin: (String, String) -> Unit
) {
    val accents = LocalVaultAccents.current
    var recoveryInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = "PIN Recovery",
                    tint = accents.neonPrimary
                )
                Text("PIN Recovery", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!uiState.recoveryStepVerified) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.recoveryMethod == RecoveryMethod.SECURITY_QUESTION,
                            onClick = {
                                recoveryInput = ""
                                onMethodChange(RecoveryMethod.SECURITY_QUESTION)
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Text("Question", maxLines = 1)
                        }
                        SegmentedButton(
                            selected = uiState.recoveryMethod == RecoveryMethod.RECOVERY_KEY,
                            onClick = {
                                recoveryInput = ""
                                onMethodChange(RecoveryMethod.RECOVERY_KEY)
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Text("Recovery Key", maxLines = 1)
                        }
                    }

                    if (uiState.recoveryMethod == RecoveryMethod.SECURITY_QUESTION) {
                        Text(
                            text = securityQuestion.ifBlank { "No security question configured" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            text = "Enter your 16-character recovery key (e.g. XXXX-XXXX-XXXX-XXXX).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it },
                        label = {
                            Text(
                                if (uiState.recoveryMethod == RecoveryMethod.SECURITY_QUESTION) {
                                    "Your Answer"
                                } else {
                                    "Recovery Key"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_verify_input")
                    )
                } else {
                    Text(
                        text = "Identity verified. Set a new 4 to 8 digit PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accents.statusGood
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_new_pin_input")
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_confirm_pin_input")
                    )
                }

                if (uiState.recoveryError != null) {
                    Text(
                        text = uiState.recoveryError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!uiState.recoveryStepVerified) {
                        onVerifyRecovery(recoveryInput)
                    } else {
                        onResetPin(newPin, confirmPin)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.neonPrimary,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier.testTag("recovery_submit_button")
            ) {
                Text(if (!uiState.recoveryStepVerified) "Verify" else "Reset PIN")
            }
        }
    )
}
