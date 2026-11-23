package com.malekfinance

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceReportExporterTest {
    private val wallet = FinanceWallet(id = 1, name = "النقد", currencyCode = "YER")
    private val rows = listOf(
        FinanceTransaction(
            amountMinor = 12_345,
            kind = "EXPENSE",
            note = "مشتريات، منزل",
            category = "منزل",
            walletId = 1,
            currencyCode = "YER",
            createdAt = 1_700_000_000_000
        ),
        FinanceTransaction(
            amountMinor = 99_000,
            kind = "INCOME",
            note = "محفظة أخرى",
            walletId = 2,
            currencyCode = "YER"
        )
    )

    @Test
    fun csvContainsOnlySelectedWalletAndEscapesCommaCells() {
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writeCsv(output, wallet, rows)
        val csv = output.toString(Charsets.UTF_8.name())
        assertTrue(csv.contains("\"مشتريات، منزل\""))
        assertTrue(csv.contains("123.45"))
        assertTrue(!csv.contains("محفظة أخرى"))
    }

    @Test
    fun xlsxIsAValidOpenXmlZipWithWorksheetAndArabicCells() {
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writeXlsx(output, wallet, rows)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        val sheet = entries["xl/worksheets/sheet1.xml"].orEmpty()
        assertTrue(sheet.contains("مشتريات، منزل"))
        assertTrue(sheet.contains("123.45"))
        assertTrue(!sheet.contains("محفظة أخرى"))
    }
}
