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
    fun csvEscapesFormulaPrefixesEvenAfterLeadingWhitespace() {
        val hostileRows = listOf(
            rows.first().copy(note = "=HYPERLINK(\"https://example.invalid\",\"click\")"),
            rows.first().copy(note = "  +SUM(1,2)"),
            rows.first().copy(note = "\t-10+20"),
            rows.first().copy(note = "\uFEFF@malicious")
        )
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writeCsv(output, wallet, hostileRows)
        val csv = output.toString(Charsets.UTF_8.name())

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://example.invalid\"\",\"\"click\"\")\""))
        assertTrue(csv.contains("\"'  +SUM(1,2)\""))
        assertTrue(csv.contains("\"'\t-10+20\""))
        assertTrue(csv.contains("\"'\uFEFF@malicious\""))
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
        assertTrue(sheet.contains("<v>123.45</v>"))
        assertTrue(!sheet.contains("محفظة أخرى"))
    }

    @Test fun xlsxRemovesForbiddenXmlControlsFromUserText() {
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writeXlsx(output, wallet, listOf(rows.first().copy(note = "a\u0000b\u000Bc & <d>")))
        var sheet = ""
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(sheet.contains("abc &amp; &lt;d&gt;"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sheet.byteInputStream())
    }

    @Test fun xlsxPreservesAmountsBeyondExcelNumericPrecisionAsText() {
        val output = ByteArrayOutputStream()
        FinanceReportExporter.writeXlsx(output, wallet, listOf(rows.first().copy(amountMinor = Long.MAX_VALUE)))
        var sheet = ""
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(sheet.contains("<c r=\"C2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">92233720368547758.07</t>"))
    }
}
