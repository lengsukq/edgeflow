package com.edgeflow.core

import com.edgeflow.core.model.VariableOperations
import com.edgeflow.core.model.VariableOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariableOperationsTest {

    @Test
    fun `assignment preserves arbitrary strings`() {
        assertEquals("night mode", VariableOperations.apply("old", "night mode", VariableOperator.ASSIGN))
    }

    @Test
    fun `arithmetic operations update numeric values`() {
        assertEquals("5", VariableOperations.apply("2", "3", VariableOperator.ADD))
        assertEquals("-1", VariableOperations.apply("2", "3", VariableOperator.SUBTRACT))
        assertEquals("6", VariableOperations.apply("2", "3", VariableOperator.MULTIPLY))
        assertEquals("2", VariableOperations.apply("6", "3", VariableOperator.DIVIDE))
        assertEquals("1", VariableOperations.apply("7", "3", VariableOperator.MODULO))
    }

    @Test
    fun `arithmetic initializes an unset variable to zero`() {
        assertEquals("4", VariableOperations.apply(null, "4", VariableOperator.ADD))
    }

    @Test
    fun `invalid numeric operations do not produce a value`() {
        assertNull(VariableOperations.apply("not numeric", "1", VariableOperator.ADD))
        assertNull(VariableOperations.apply("1", "0", VariableOperator.DIVIDE))
        assertNull(VariableOperations.apply("1", "0", VariableOperator.MODULO))
    }
}
