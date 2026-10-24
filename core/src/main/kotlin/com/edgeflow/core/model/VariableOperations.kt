package com.edgeflow.core.model

import java.math.BigDecimal
import java.math.MathContext

/** Applies a configured arithmetic operation without mutating the caller's state. */
object VariableOperations {

    /**
     * Returns the updated value, or null when the requested numeric operation is invalid.
     * An unset variable starts at zero for arithmetic; assignment accepts any string.
     */
    fun apply(current: String?, operand: String, operator: VariableOperator): String? {
        if (operator == VariableOperator.ASSIGN) return operand

        val left = if (current == null) BigDecimal.ZERO else current.toBigDecimalOrNull() ?: return null
        val right = operand.toBigDecimalOrNull() ?: return null
        val result = when (operator) {
            VariableOperator.ASSIGN -> return operand
            VariableOperator.ADD -> left + right
            VariableOperator.SUBTRACT -> left - right
            VariableOperator.MULTIPLY -> left * right
            VariableOperator.DIVIDE -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) return null
                left.divide(right, MathContext.DECIMAL128)
            }
            VariableOperator.MODULO -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) return null
                left.remainder(right)
            }
        }
        return result.stripTrailingZeros().toPlainString()
    }
}
