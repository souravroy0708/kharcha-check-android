package com.kharchacheck.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The add-screen keypad accepts sums like "120+45-20" so people can total a bill without leaving the app.
 * Only + and − are supported on purpose: it covers splitting and adding items, and stays unambiguous.
 */
object AmountExpression {
    const val MAX_PAISE = 99_99_99_999_00L // ₹99.99 crore guard

    /** Returns paise for a valid, positive expression, or null. Trailing operators are ignored while typing. */
    fun evaluate(expr: String): Long? {
        val s = expr.replace("−", "-").replace(" ", "").trimEnd('+', '-')
        if (s.isEmpty()) return null
        var total = BigDecimal.ZERO
        var sign = 1
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return false
            val v = num.toString().toBigDecimalOrNull() ?: return false
            total = if (sign > 0) total + v else total - v
            num.clear()
            return true
        }
        for (ch in s) {
            when {
                ch.isDigit() || ch == '.' -> num.append(ch)
                ch == '+' || ch == '-' -> { if (!flush()) return null; sign = if (ch == '+') 1 else -1 }
                else -> return null
            }
        }
        if (!flush()) return null
        if (total.signum() <= 0) return null
        val paise = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        return paise.takeIf { it in 1..MAX_PAISE }
    }

    fun hasOperator(expr: String) = expr.drop(1).any { it == '+' || it == '-' || it == '−' }

    /** Applies one keypad press with sensible guards (one dot per number, max 2 decimals, no double operators). */
    fun press(current: String, key: String): String {
        val lastNum = current.split('+', '-', '−').last()
        return when (key) {
            "⌫" -> current.dropLast(1)
            "C" -> ""
            "+", "−" -> when {
                current.isEmpty() -> current
                current.last() in "+−-" -> current.dropLast(1) + key
                else -> current + key
            }
            "." -> when {
                lastNum.contains('.') -> current
                lastNum.isEmpty() -> current + "0."
                else -> current + "."
            }
            else -> {
                val decimals = lastNum.substringAfter('.', "")
                when {
                    lastNum.contains('.') && decimals.length + key.length > 2 -> current
                    lastNum == "0" && key != "." -> current.dropLast(1) + key.trimStart('0').ifEmpty { "0" }
                    current.length >= 24 -> current
                    else -> current + key
                }
            }
        }
    }
}
