package com.malekfinance

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceDraftPolicyTest {
    private val wallets = listOf(
        FinanceWallet(id = 1, name = "النقد"), FinanceWallet(id = 2, name = "البنك")
    )

    @Test fun negativeSubUnitAmountsKeepTheirSign() {
        listOf(-1L to "-0.01", -50L to "-0.50", -99L to "-0.99", -100L to "-1.00", 0L to "0.00", 50L to "0.50").forEach { (value, expected) ->
            assertEquals("$expected YER", FinanceMoney.format(value, "YER"))
        }
    }

    @Test fun largeAggregateNeverPassesThroughFloatingPoint() {
        assertEquals("-999999999999999999999999.99 USD", FinanceMoney.format(BigInteger("-99999999999999999999999999"), "USD"))
    }

    @Test fun transferDirectionDoesNotDependOnWalletOrdering() {
        val text = "حول 100 من البنك إلى النقد"
        assertEquals(2L to 1L, FinanceVoiceDraftPolicy.transferParties(text, wallets))
        assertEquals(2L to 1L, FinanceVoiceDraftPolicy.transferParties(text, wallets.reversed()))
        assertEquals(1L to 2L, FinanceVoiceDraftPolicy.transferParties("حول من النقد الى البنك 100 ريال", wallets))
    }

    @Test fun partialNamesDoNotMatch() {
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من النقدية الى البنك 100", wallets))
    }

    @Test fun duplicatedOrOverlappingNamesRequireManualSelection() {
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من البنك إلى النقد", wallets + wallets[1].copy(id = 3)))
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من البنك إلى النقد الرئيسي", wallets + FinanceWallet(id = 3, name = "النقد الرئيسي")))
    }

    @Test fun unknownSameOrDifferentCurrencyWalletsAreNotSuggested() {
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من مجهول إلى النقد", wallets))
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من النقد إلى النقد", wallets))
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من البنك إلى النقد", listOf(wallets[0], wallets[1].copy(currencyCode = "USD"))))
    }

    @Test fun multipleDirectionClausesRequireClarification() {
        assertEquals(null to null, FinanceVoiceDraftPolicy.transferParties("حول من البنك إلى النقد ومن النقد إلى البنك", wallets))
    }
}
