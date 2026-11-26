package com.malekfinance

import java.math.BigDecimal
import java.math.BigInteger

/** Exact two-decimal display, including negative amounts smaller than one unit. */
object FinanceMoney {
    fun format(minor: BigInteger, currency: String): String =
        BigDecimal(minor, 2).toPlainString() + " " + currency

    fun format(minor: Long, currency: String): String = format(BigInteger.valueOf(minor), currency)
}
