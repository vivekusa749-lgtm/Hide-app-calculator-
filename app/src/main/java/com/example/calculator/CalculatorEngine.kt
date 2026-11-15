package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

data class CalculatorState(
    val displayText: String = "0",
    val expressionText: String = "",
    val pendingOperator: CalcOperator? = null,
    val accumulator: BigDecimal? = null,
    val isEnteringNewNumber: Boolean = true,
    val hasError: Boolean = false,
    val isRawPinCandidate: Boolean = true
)

class CalculatorEngine {
    private val mathContext = MathContext(15, RoundingMode.HALF_UP)

    fun inputDigit(state: CalculatorState, digit: Char): CalculatorState {
        if (state.hasError) {
            return CalculatorState(
                displayText = digit.toString(),
                isEnteringNewNumber = false,
                isRawPinCandidate = true
            )
        }
        val nextText = if (state.isEnteringNewNumber || state.displayText == "0") {
            digit.toString()
        } else {
            if (state.displayText.length >= 16) state.displayText
            else state.displayText + digit
        }
        return state.copy(
            displayText = nextText,
            isEnteringNewNumber = false,
            isRawPinCandidate = state.pendingOperator == null &&
                state.accumulator == null &&
                state.expressionText.isEmpty() &&
                !nextText.contains(".") &&
                !nextText.startsWith("-")
        )
    }

    fun inputDecimal(state: CalculatorState): CalculatorState {
        if (state.hasError) {
            return CalculatorState(
                displayText = "0.",
                isEnteringNewNumber = false,
                isRawPinCandidate = false
            )
        }
        if (state.isEnteringNewNumber) {
            return state.copy(
                displayText = "0.",
                isEnteringNewNumber = false,
                isRawPinCandidate = false
            )
        }
        if (state.displayText.contains(".")) return state
        return state.copy(
            displayText = state.displayText + ".",
            isRawPinCandidate = false
        )
    }

    fun backspace(state: CalculatorState): CalculatorState {
        if (state.hasError) return CalculatorState()
        if (state.isEnteringNewNumber) return state
        val trimmed = state.displayText.dropLast(1)
        val next = if (trimmed.isEmpty() || trimmed == "-") "0" else trimmed
        return state.copy(
            displayText = next,
            isEnteringNewNumber = next == "0" && state.pendingOperator == null
        )
    }

    fun clearAll(): CalculatorState = CalculatorState()

    fun toggleSign(state: CalculatorState): CalculatorState {
        if (state.hasError || state.displayText == "0") return state
        val next = if (state.displayText.startsWith("-")) {
            state.displayText.drop(1)
        } else {
            "-" + state.displayText
        }
        return state.copy(displayText = next, isRawPinCandidate = false)
    }

    fun applyPercent(state: CalculatorState): CalculatorState {
        if (state.hasError) return state
        val current = state.displayText.toBigDecimalOrNull() ?: return state
        val percentValue = if (state.accumulator != null &&
            (state.pendingOperator == CalcOperator.ADD || state.pendingOperator == CalcOperator.SUBTRACT)
        ) {
            state.accumulator.multiply(current, mathContext)
                .divide(BigDecimal(100), mathContext)
        } else {
            current.divide(BigDecimal(100), mathContext)
        }
        return state.copy(
            displayText = formatBigDecimal(percentValue),
            isEnteringNewNumber = false,
            isRawPinCandidate = false
        )
    }

    fun applyOperator(state: CalculatorState, operator: CalcOperator): CalculatorState {
        if (state.hasError) return CalculatorState()
        val current = state.displayText.toBigDecimalOrNull() ?: BigDecimal.ZERO

        if (state.accumulator != null && state.pendingOperator != null && !state.isEnteringNewNumber) {
            val evaluated = evaluateBinary(state.accumulator, current, state.pendingOperator)
                ?: return CalculatorState(displayText = "Error", hasError = true, isRawPinCandidate = false)
            val formatted = formatBigDecimal(evaluated)
            return CalculatorState(
                displayText = formatted,
                expressionText = "$formatted ${operator.symbol}",
                pendingOperator = operator,
                accumulator = evaluated,
                isEnteringNewNumber = true,
                isRawPinCandidate = false
            )
        }

        val acc = state.accumulator ?: current
        val formattedAcc = formatBigDecimal(acc)
        return state.copy(
            displayText = formattedAcc,
            expressionText = "$formattedAcc ${operator.symbol}",
            pendingOperator = operator,
            accumulator = acc,
            isEnteringNewNumber = true,
            isRawPinCandidate = false
        )
    }

    fun evaluateEquals(state: CalculatorState): CalculatorState {
        if (state.hasError) return CalculatorState()
        val pending = state.pendingOperator ?: return state.copy(
            expressionText = "",
            isEnteringNewNumber = true,
            isRawPinCandidate = false
        )
        val acc = state.accumulator ?: return state
        val current = state.displayText.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val result = evaluateBinary(acc, current, pending)
            ?: return CalculatorState(
                displayText = "Error",
                expressionText = "${formatBigDecimal(acc)} ${pending.symbol} ${formatBigDecimal(current)}",
                hasError = true,
                isRawPinCandidate = false
            )
        val formatted = formatBigDecimal(result)
        return CalculatorState(
            displayText = formatted,
            expressionText = "${formatBigDecimal(acc)} ${pending.symbol} ${formatBigDecimal(current)} =",
            pendingOperator = null,
            accumulator = null,
            isEnteringNewNumber = true,
            hasError = false,
            isRawPinCandidate = false
        )
    }

    private fun evaluateBinary(a: BigDecimal, b: BigDecimal, op: CalcOperator): BigDecimal? =
        when (op) {
            CalcOperator.ADD -> a.add(b, mathContext)
            CalcOperator.SUBTRACT -> a.subtract(b, mathContext)
            CalcOperator.MULTIPLY -> a.multiply(b, mathContext)
            CalcOperator.DIVIDE -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) null
                else a.divide(b, 12, RoundingMode.HALF_UP)
            }
        }

    private fun formatBigDecimal(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val plain = if (stripped.scale() <= 0 && stripped.abs() < BigDecimal("10000000000000")) {
            stripped.toBigInteger().toString()
        } else {
            val s = stripped.toPlainString()
            if (s.length > 16) stripped.round(MathContext(10, RoundingMode.HALF_UP)).toString() else s
        }
        return if (plain == "-0") "0" else plain
    }
}
