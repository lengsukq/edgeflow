package com.edgeflow.ui

import com.edgeflow.core.ConditionId
import com.edgeflow.core.model.Condition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionalConditionBuilderTest {

    @Test
    fun `builds inclusive battery ranges at the supported boundaries`() {
        assertEquals(
            Condition.BatteryLevel(min = 0, max = 100),
            buildConditionalCondition(ConditionId.BATTERY_LEVEL, "0", "100"),
        )
        assertEquals(
            Condition.BatteryLevel(min = 35, max = 35),
            buildConditionalCondition(ConditionId.BATTERY_LEVEL, "35", "35"),
        )
    }

    @Test
    fun `rejects invalid and reversed battery ranges`() {
        assertNull(buildConditionalCondition(ConditionId.BATTERY_LEVEL, "-1", "50"))
        assertNull(buildConditionalCondition(ConditionId.BATTERY_LEVEL, "10", "101"))
        assertNull(buildConditionalCondition(ConditionId.BATTERY_LEVEL, "70", "20"))
        assertNull(buildConditionalCondition(ConditionId.BATTERY_LEVEL, "high", "100"))
    }

    @Test
    fun `known flags remain configurable and unknown conditions stay rejected`() {
        assertEquals(
            Condition.Flag(ConditionId.SCREEN_ON),
            buildConditionalCondition(ConditionId.SCREEN_ON, "", ""),
        )
        assertNull(buildConditionalCondition(ConditionId.INVALID, "", ""))
    }

    @Test
    fun `time parser accepts valid 24-hour boundaries only`() {
        assertEquals(0, parseConditionTimeMinutes("00:00"))
        assertEquals(1439, parseConditionTimeMinutes("23:59"))
        assertNull(parseConditionTimeMinutes("24:00"))
        assertNull(parseConditionTimeMinutes("8:00"))
        assertNull(parseConditionTimeMinutes("12:60"))
    }

    @Test
    fun `composite conditions reject empty or incomplete branches`() {
        val screenOn = Condition.Flag(ConditionId.SCREEN_ON)
        assertTrue(isValidCondition(Condition.And(listOf(screenOn, Condition.BatteryLevel(20, 80)))))
        assertTrue(isValidCondition(Condition.Not(screenOn)))
        assertFalse(isValidCondition(Condition.And()))
        assertFalse(isValidCondition(Condition.Or(listOf(Condition.Unknown()))))
        assertFalse(isValidCondition(Condition.Not()))
    }
}
