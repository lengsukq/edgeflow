package com.edgeflow.hook

import com.edgeflow.core.ConfigCodec
import com.edgeflow.core.model.Condition
import com.edgeflow.core.model.ConditionOperator
import com.edgeflow.core.model.VariableOperator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionEvaluatorTest {

    @Test
    fun `not fails closed when its child is unavailable`() {
        val evaluator = ConditionEvaluator()
        val unavailable = Condition.Variable("missing", "value")

        assertFalse(evaluator.evaluate(unavailable, emptyMap()))
        assertFalse(evaluator.evaluate(Condition.Not(unavailable), emptyMap()))

        // A later false child must not erase an earlier unavailable read either.
        val conjunction = Condition.And(
            listOf(unavailable, Condition.Variable("state", "idle")),
        )
        assertFalse(evaluator.evaluate(Condition.Not(conjunction), mapOf("state" to "ready")))
    }

    @Test
    fun `not negates supported true and false conditions normally`() {
        val evaluator = ConditionEvaluator()
        val matching = Condition.Variable("state", "ready")
        val nonMatching = Condition.Variable("state", "idle")
        val variables = mapOf("state" to "ready")

        assertTrue(evaluator.evaluate(matching, variables))
        assertFalse(evaluator.evaluate(Condition.Not(matching), variables))
        assertFalse(evaluator.evaluate(nonMatching, variables))
        assertTrue(evaluator.evaluate(Condition.Not(nonMatching), variables))
    }

    @Test
    fun `variable operators perform the requested numeric comparison`() {
        assertEquals(true, compareConditionValues("10", "2", ConditionOperator.GREATER_THAN))
        assertEquals(true, compareConditionValues("2", "2.0", ConditionOperator.EQUAL))
        assertEquals(true, compareConditionValues("2", "2", ConditionOperator.GREATER_THAN_OR_EQUAL))
        assertEquals(true, compareConditionValues("1", "2", ConditionOperator.LESS_THAN))
        assertEquals(true, compareConditionValues("2", "2", ConditionOperator.LESS_THAN_OR_EQUAL))
        assertEquals(true, compareConditionValues("1", "2", ConditionOperator.NOT_EQUAL))
        assertEquals(false, compareConditionValues("2", "10", ConditionOperator.GREATER_THAN))
    }

    @Test
    fun `variable equality supports text but ordered comparisons require finite numbers`() {
        assertTrue(compareConditionValues("night", "night", ConditionOperator.EQUAL) == true)
        assertTrue(compareConditionValues("night", "day", ConditionOperator.NOT_EQUAL) == true)
        assertNull(compareConditionValues("night", "1", ConditionOperator.LESS_THAN))
        assertNull(compareConditionValues("NaN", "1", ConditionOperator.EQUAL))
        assertNull(compareConditionValues("Infinity", "1", ConditionOperator.GREATER_THAN))
    }

    @Test
    fun `legacy arithmetic condition operators fail closed without evaluating arithmetic`() {
        assertNull(compareConditionValues("10", "0", ConditionOperator.LEGACY_DIVIDE))
        assertNull(compareConditionValues("10", "3", ConditionOperator.LEGACY_ADD))

        // Old source callers remain constructible, but old non-comparison operators
        // are not silently assigned invented predicate semantics.
        assertEquals(
            ConditionOperator.LEGACY_DIVIDE,
            Condition.Variable("x", "0", VariableOperator.DIVIDE).operator,
        )
    }

    @Test
    fun `power connection means an attached power source, not active charging`() {
        assertFalse(isPowerSourceConnected(0))
        assertTrue(isPowerSourceConnected(1))
        assertTrue(isPowerSourceConnected(2))
        assertTrue(isPowerSourceConnected(4))
    }

    @Test
    fun `date masks select one-based days and months`() {
        val selectedDays = (1 shl 0) or (1 shl 14) or (1 shl 30)
        assertTrue(isOneBasedMaskSelected(selectedDays, 1, 31))
        assertTrue(isOneBasedMaskSelected(selectedDays, 15, 31))
        assertTrue(isOneBasedMaskSelected(selectedDays, 31, 31))
        assertFalse(isOneBasedMaskSelected(selectedDays, 2, 31))
        assertFalse(isOneBasedMaskSelected(selectedDays, 0, 31))
        assertFalse(isOneBasedMaskSelected(selectedDays, 32, 31))

        val selectedMonths = (1 shl 0) or (1 shl 11)
        assertTrue(isOneBasedMaskSelected(selectedMonths, 1, 12))
        assertTrue(isOneBasedMaskSelected(selectedMonths, 12, 12))
        assertFalse(isOneBasedMaskSelected(selectedMonths, 2, 12))
        assertFalse(isOneBasedMaskSelected(selectedMonths, 13, 12))
    }

    @Test
    fun `new date conditions and existing variable operator serialize compatibly`() {
        val conditions = listOf<Condition>(
            Condition.DaysOfMonth(mask = (1 shl 1) or (1 shl 30)),
            Condition.Months(mask = (1 shl 0) or (1 shl 11)),
            Condition.Variable("count", "5", ConditionOperator.GREATER_THAN),
        )
        conditions.forEach { condition ->
            val json = ConfigCodec.json.encodeToString(Condition.serializer(), condition)
            assertEquals(condition, ConfigCodec.json.decodeFromString(Condition.serializer(), json))
        }

        val oldVariableJson = """{"type":"variable","name":"count","value":"5","operator":"ASSIGN","id":37}"""
        val oldVariable = ConfigCodec.json.decodeFromString<Condition>(oldVariableJson)
        assertEquals(Condition.Variable("count", "5"), oldVariable)
        assertTrue(ConfigCodec.json.encodeToString(Condition.serializer(), oldVariable).contains("\"operator\":\"ASSIGN\""))

        val oldArithmeticJson = """{"type":"variable","name":"count","value":"5","operator":"DIVIDE","id":37}"""
        assertEquals(
            ConditionOperator.LEGACY_DIVIDE,
            (ConfigCodec.json.decodeFromString<Condition>(oldArithmeticJson) as Condition.Variable).operator,
        )
    }
}
