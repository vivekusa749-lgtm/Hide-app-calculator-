package com.example

import com.example.calculator.CalcOperator
import com.example.calculator.CalculatorEngine
import com.example.calculator.CalculatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {
    private val engine = CalculatorEngine()

    @Test
    fun `decimal addition avoids floating point drift`() {
        var s = CalculatorState()
        s = engine.inputDigit(s, '0')
        s = engine.inputDecimal(s)
        s = engine.inputDigit(s, '1')
        s = engine.applyOperator(s, CalcOperator.ADD)
        s = engine.inputDigit(s, '0')
        s = engine.inputDecimal(s)
        s = engine.inputDigit(s, '2')
        s = engine.evaluateEquals(s)

        assertEquals("0.3", s.displayText)
        assertFalse(s.hasError)
    }

    @Test
    fun `percentage with addition computes relative percent`() {
        var s = CalculatorState()
        s = engine.inputDigit(s, '2')
        s = engine.inputDigit(s, '0')
        s = engine.inputDigit(s, '0')
        s = engine.applyOperator(s, CalcOperator.ADD)
        s = engine.inputDigit(s, '1')
        s = engine.inputDigit(s, '5')
        s = engine.applyPercent(s)
        s = engine.evaluateEquals(s)

        assertEquals("230", s.displayText)
    }

    @Test
    fun `division by zero returns Error state`() {
        var s = CalculatorState()
        s = engine.inputDigit(s, '9')
        s = engine.applyOperator(s, CalcOperator.DIVIDE)
        s = engine.inputDigit(s, '0')
        s = engine.evaluateEquals(s)

        assertTrue(s.hasError)
        assertEquals("Error", s.displayText)
    }

    @Test
    fun `raw pin candidate is true only for unoperated integer input`() {
        var s = CalculatorState()
        "1337".forEach { s = engine.inputDigit(s, it) }
        assertTrue(s.isRawPinCandidate)

        s = engine.applyOperator(s, CalcOperator.ADD)
        s = engine.inputDigit(s, '5')
        assertFalse(s.isRawPinCandidate)
    }
}
