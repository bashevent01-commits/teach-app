package com.knowapp.android.ui.statements

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 40f
private const val ROW_H = 18f

object StatementPdf {
    private val number = DecimalFormat("#,##0.00")

    fun build(file: File, title: String, subtitle: String, rows: List<StatementRow>, income: BigDecimal, expense: BigDecimal) {
        val doc = PdfDocument()
        val body = Paint().apply { textSize = 10f; color = 0xFF10201D.toInt() }
        val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val head = Paint(bold).apply { textSize = 18f; color = 0xFF00695F.toInt() }
        val muted = Paint(body).apply { color = 0xFF5D6F6B.toInt() }
        val rightBody = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = 0xFFE2E8E6.toInt(); strokeWidth = 1f }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 14f

        fun header() {
            canvas.drawText("KNOW", MARGIN, y, head)
            y += 18f
            canvas.drawText(title, MARGIN, y, bold)
            y += 14f
            canvas.drawText(subtitle, MARGIN, y, muted)
            y += 22f
            canvas.drawText("Date", MARGIN, y, bold)
            canvas.drawText("Category", MARGIN + 75f, y, bold)
            canvas.drawText("Method", MARGIN + 260f, y, bold)
            canvas.drawText("Money in", MARGIN + 395f, y, rightBold)
            canvas.drawText("Money out", PAGE_W - MARGIN, y, rightBold)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
            y += ROW_H - 4f
        }

        header()
        for (row in rows) {
            if (y > PAGE_H - MARGIN - 60f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
                canvas = page.canvas
                y = MARGIN + 14f
                header()
            }
            canvas.drawText(row.date.toString(), MARGIN, y, body)
            canvas.drawText(row.category.take(30), MARGIN + 75f, y, body)
            canvas.drawText(row.methodLabel, MARGIN + 260f, y, body)
            if (row.type == "income") canvas.drawText(number.format(row.amount), MARGIN + 395f, y, rightBody)
            else canvas.drawText(number.format(row.amount), PAGE_W - MARGIN, y, rightBody)
            y += ROW_H
        }

        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
        y += ROW_H
        canvas.drawText("Totals", MARGIN, y, bold)
        canvas.drawText(number.format(income), MARGIN + 395f, y, rightBold)
        canvas.drawText(number.format(expense), PAGE_W - MARGIN, y, rightBold)
        y += ROW_H
        canvas.drawText("Net (KES)", MARGIN, y, bold)
        canvas.drawText(number.format(income - expense), PAGE_W - MARGIN, y, rightBold)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

// Hands an already-written PDF to Android's print dialog, which also offers Save as PDF
class FilePrintAdapter(private val file: File, private val jobName: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).use { out -> input.copyTo(out) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
