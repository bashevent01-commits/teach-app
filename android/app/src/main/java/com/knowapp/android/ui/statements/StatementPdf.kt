package com.knowapp.android.ui.statements

import android.graphics.Canvas
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
import java.time.format.DateTimeFormatter

data class StatementPdfData(
    val institution: String?,
    val title: String,
    val account: String,
    val period: String,
    val staff: String?,
    val preparedBy: String,
    val generated: String,
    val lines: List<StatementLine>,
    val opening: BigDecimal?,
    val closing: BigDecimal?,
    val income: BigDecimal,
    val expense: BigDecimal,
)

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val LEFT = 36f
private const val RIGHT = 559f
private const val ROW_H = 17f
private const val FIRST_CAP = 30
private const val NEXT_CAP = 41

object StatementPdf {
    private val number = DecimalFormat("#,##0.00")
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun fit(p: Paint, text: String, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun build(file: File, d: StatementPdfData) {
        val ink = 0xFF10201D.toInt()
        val muted = 0xFF5D6F6B.toInt()
        val teal = 0xFF0F766E.toInt()
        val rule = 0xFFD9E2E0.toInt()
        val zebra = 0xFFF3F7F6.toInt()
        val headFill = 0xFFE3F0EE.toInt()

        fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val body = paint(8.5f, ink)
        val bodyRight = paint(8.5f, ink, align = Paint.Align.RIGHT)
        val small = paint(7.5f, muted)
        val smallRight = paint(7.5f, muted, align = Paint.Align.RIGHT)
        val headText = paint(8f, teal, bold = true)
        val headRight = paint(8f, teal, bold = true, align = Paint.Align.RIGHT)
        val fill = Paint().apply { style = Paint.Style.FILL }
        val stroke = Paint().apply { color = rule; strokeWidth = 0.8f }

        val showBal = d.lines.firstOrNull()?.balance != null || d.opening != null
        val inRight = if (showBal) 432f else 494f
        val outRight = if (showBal) 494f else RIGHT
        val dateX = LEFT
        val descX = LEFT + 78f
        val refX = LEFT + 220f
        val methX = LEFT + 298f

        // How many rows each page holds; the last page keeps room for the totals
        val sizes = mutableListOf<Int>()
        var remaining = d.lines.size
        var cap = FIRST_CAP
        while (remaining > 0) {
            val take = minOf(cap, remaining)
            sizes += take
            remaining -= take
            cap = NEXT_CAP
        }
        val lastCap = if (sizes.size <= 1) FIRST_CAP else NEXT_CAP
        if (sizes.isEmpty() || sizes.last() > lastCap - 4) sizes += 0

        val doc = PdfDocument()
        var index = 0

        sizes.forEachIndexed { pageIndex, count ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
            val canvas: Canvas = page.canvas
            var y: Float

            fun tableHeader(top: Float): Float {
                fill.color = headFill
                canvas.drawRect(LEFT, top, RIGHT, top + 22f, fill)
                val base = top + 14.5f
                canvas.drawText("Date", dateX + 4f, base, headText)
                canvas.drawText("Description", descX, base, headText)
                canvas.drawText("Reference", refX, base, headText)
                canvas.drawText("Method", methX, base, headText)
                canvas.drawText("Money in", inRight, base, headRight)
                canvas.drawText("Money out", outRight, base, headRight)
                if (showBal) canvas.drawText("Balance", RIGHT - 2f, base, headRight)
                return top + 22f
            }

            if (pageIndex == 0) {
                fill.color = teal
                canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 84f, fill)
                canvas.drawText("KNOW", LEFT, 46f, paint(24f, 0xFFFFFFFF.toInt(), bold = true))
                canvas.drawText("Account statement", LEFT, 64f, paint(11f, 0xCCFFFFFF.toInt()))
                canvas.drawText(d.generated, RIGHT, 46f, paint(9f, 0xCCFFFFFF.toInt(), align = Paint.Align.RIGHT))

                var iy = 112f
                canvas.drawText(d.institution ?: "Institution", LEFT, iy, paint(15f, ink, bold = true))
                iy += 17f
                canvas.drawText(d.title, LEFT, iy, paint(11f, teal, bold = true))
                iy += 15f
                canvas.drawText("Account: ${d.account}     Period: ${d.period}", LEFT, iy, paint(9f, muted))
                iy += 13f
                val who = listOfNotNull(d.staff?.let { "Staff: $it" }, if (d.preparedBy.isNotBlank()) "Prepared by ${d.preparedBy}" else null).joinToString("     ")
                if (who.isNotEmpty()) canvas.drawText(who, LEFT, iy, paint(9f, muted))

                val boxes = buildList {
                    if (showBal && d.opening != null) add("Opening balance" to d.opening)
                    add("Money in" to d.income)
                    add("Money out" to d.expense)
                    if (showBal && d.closing != null) add("Closing balance" to d.closing) else add("Net" to (d.income - d.expense))
                }
                val gap = 8f
                val boxW = (RIGHT - LEFT - gap * (boxes.size - 1)) / boxes.size
                boxes.forEachIndexed { i, (label, value) ->
                    val x = LEFT + i * (boxW + gap)
                    fill.color = zebra
                    canvas.drawRoundRect(x, 176f, x + boxW, 222f, 6f, 6f, fill)
                    canvas.drawText(label, x + 10f, 194f, small)
                    canvas.drawText("KES ${number.format(value)}", x + 10f, 212f, paint(10.5f, ink, bold = true))
                }
                y = tableHeader(238f)
            } else {
                canvas.drawText("KNOW  ·  ${d.title}", LEFT, 44f, paint(9f, teal, bold = true))
                canvas.drawText(d.period, RIGHT, 44f, paint(9f, muted, align = Paint.Align.RIGHT))
                y = tableHeader(56f)
            }

            for (i in 0 until count) {
                val line = d.lines[index++]
                val r = line.row
                if (i % 2 == 1) {
                    fill.color = zebra
                    canvas.drawRect(LEFT, y, RIGHT, y + ROW_H, fill)
                }
                val base = y + 11.5f
                val time = r.time?.format(timeFmt) ?: ""
                canvas.drawText("${r.date.format(dateFmt)} $time".trim(), dateX + 4f, base, body)
                val description = listOfNotNull(r.category, r.party ?: r.note).joinToString(" · ")
                canvas.drawText(fit(body, description, 134f), descX, base, body)
                canvas.drawText(fit(body, r.reference ?: "-", 74f), refX, base, body)
                canvas.drawText(r.methodLabel, methX, base, body)
                val amountText = number.format(r.amount)
                if (r.type == "income") canvas.drawText(amountText, inRight, base, bodyRight) else canvas.drawText(amountText, outRight, base, bodyRight)
                line.balance?.let { canvas.drawText(number.format(it), RIGHT - 2f, base, bodyRight) }
                y += ROW_H
            }

            if (pageIndex == sizes.lastIndex) {
                if (d.lines.isEmpty()) {
                    canvas.drawText("No entries for this selection.", LEFT + 4f, y + 14f, small)
                    y += ROW_H
                }
                y += 6f
                canvas.drawLine(LEFT, y, RIGHT, y, stroke)
                y += 14f
                canvas.drawText("Totals", dateX + 4f, y, paint(9f, ink, bold = true))
                canvas.drawText(number.format(d.income), inRight, y, paint(9f, ink, bold = true, align = Paint.Align.RIGHT))
                canvas.drawText(number.format(d.expense), outRight, y, paint(9f, ink, bold = true, align = Paint.Align.RIGHT))
                if (showBal && d.closing != null) {
                    y += 15f
                    canvas.drawText("Closing balance", dateX + 4f, y, paint(9f, teal, bold = true))
                    canvas.drawText("KES ${number.format(d.closing)}", RIGHT - 2f, y, paint(9.5f, teal, bold = true, align = Paint.Align.RIGHT))
                }
                y += 20f
                canvas.drawText("End of statement. Amounts are in Kenya shillings (KES).", LEFT + 4f, y, small)
            }

            canvas.drawLine(LEFT, 812f, RIGHT, 812f, stroke)
            canvas.drawText("Generated by KNOW  ·  ${d.generated}", LEFT, 826f, small)
            canvas.drawText("Page ${pageIndex + 1} of ${sizes.size}", RIGHT, 826f, smallRight)
            doc.finishPage(page)
        }

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
