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

    @Test fun singleLongNoteContinuesAcrossPages() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File.createTempFile("long-report", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { output ->
                FinanceReportExporter.writePdf(output, FinanceWallet(id = 1, name = "نقد"), listOf(
                    FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = ("تفاصيل شراء طويلة ").repeat(3000))
                ))
            }
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Long note must not be clipped to a single page", renderer.pageCount > 2)
                }
            }
        } finally { file.delete() }
    }
}
