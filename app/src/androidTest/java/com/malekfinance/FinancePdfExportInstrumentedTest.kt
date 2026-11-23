package com.malekfinance

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancePdfExportInstrumentedTest {
    @Test
    fun pdfExportProducesPdfDocumentBytes() {
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writePdf(
            output,
            FinanceWallet(id = 1, name = "النقد", currencyCode = "YER"),
            listOf(
                FinanceTransaction(
                    amountMinor = 12_345,
                    kind = "EXPENSE",
                    note = "مشتريات المنزل",
                    category = "منزل",
                    walletId = 1,
                    currencyCode = "YER"
                )
            )
        )
        val bytes = output.toByteArray()
        assertTrue(bytes.size > 100)
        assertTrue(String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "%PDF")
    }
}
