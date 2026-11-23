package com.malekfinance

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FinanceReportExporter {
    fun writeCsv(output: OutputStream, wallet: FinanceWallet, transactions: List<FinanceTransaction>) {
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        output.writer(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("التاريخ,النوع,المبلغ,العملة,التصنيف,الوصف")
            transactions.filter { it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
                .sortedBy { it.createdAt }
                .forEach { row ->
                    writer.appendLine(listOf(
                        dateText(row.createdAt),
                        row.kind,
                        decimalAmount(row.amountMinor),
                        row.currencyCode,
                        row.category,
                        row.note
                    ).joinToString(",") { csvCell(it) })
                }
            writer.flush()
        }
    }

    fun writeXlsx(output: OutputStream, wallet: FinanceWallet, transactions: List<FinanceTransaction>) {
        val rows = buildList {
            add(listOf("التاريخ", "النوع", "المبلغ", "العملة", "التصنيف", "الوصف"))
            transactions.filter { it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
                .sortedBy { it.createdAt }
                .forEach { add(listOf(dateText(it.createdAt), it.kind, decimalAmount(it.amountMinor), it.currencyCode, it.category, it.note)) }
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="العمليات" sheetId="1" r:id="rId1"/></sheets></workbook>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val xml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rowIndex, row ->
                    append("<row r=\"").append(rowIndex + 1).append("\">")
                    row.forEachIndexed { columnIndex, value ->
                        append("<c r=\"").append(columnName(columnIndex + 1)).append(rowIndex + 1)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(xmlEscape(value)).append("</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            zip.write(xml.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun writePdf(output: OutputStream, wallet: FinanceWallet, transactions: List<FinanceTransaction>) {
        val rows = transactions.filter { it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
            .sortedByDescending { it.createdAt }
        val lines = buildList {
            add("تقرير مالي — ${wallet.name} (${wallet.currencyCode})")
            add("عدد العمليات: ${rows.size}")
            add(" ")
            rows.forEach { add("${dateText(it.createdAt)} | ${it.kind} | ${decimalAmount(it.amountMinor)} ${it.currencyCode} | ${it.category} | ${it.note}") }
        }
        val document = PdfDocument()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.BLACK }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; color = android.graphics.Color.BLACK; isFakeBoldText = true }
        try {
            lines.chunked(42).forEachIndexed { pageIndex, pageLines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
                var y = 40
                pageLines.forEachIndexed { index, line ->
                    val paint = if (pageIndex == 0 && index == 0) titlePaint else textPaint
                    val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, 535)
                        .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                        .setTextDirection(TextDirectionHeuristics.RTL)
                        .setIncludePad(false)
                        .build()
                    page.canvas.save()
                    page.canvas.translate(30f, y.toFloat())
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    y += layout.height + 8
                }
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun decimalAmount(minor: Long) =
        java.math.BigDecimal.valueOf(minor, 2).toPlainString()

    private fun csvCell(value: String): String {
        val first = value.firstOrNull()
        val safe = if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\\t' || first == '\\r') "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun dateText(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(timestamp))

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun columnName(number: Int): String {
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            result.insert(0, ('A'.code + remainder).toChar())
            n = (n - 1) / 26
        }
        return result.toString()
    }
}
