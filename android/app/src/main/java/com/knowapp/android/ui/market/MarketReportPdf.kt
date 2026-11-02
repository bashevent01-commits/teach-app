package com.knowapp.android.ui.market

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.ui.statements.FilePrintAdapter
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PW = 595
private const val PH = 842
private const val PL = 36f
private const val PR = 559f

fun kesText(v: Double): String = "KES " + String.format(Locale.US, "%,.2f", v)

object MarketReportPdf {
    private val ink = 0xFF10201D.toInt()
    private val muted = 0xFF5D6F6B.toInt()
    private val teal = 0xFF0F766E.toInt()
    private val deep = 0xFF134E4A.toInt()
    private val mint = 0xFF99F6E4.toInt()
    private val rule = 0xFFD9E2E0.toInt()
    private val zebra = 0xFFF3F7F6.toInt()
    private val tile = 0xFFE7F3F1.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = align
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun fit(p: Paint, text: String, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun build(file: File, d: CategoryDetailOut, demo: Boolean) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 0.8f }
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, 1).create())
        val c: Canvas = page.canvas

        // Header band
        fill.color = teal
        c.drawRect(0f, 0f, PW.toFloat(), 96f, fill)
        fill.color = 0x14FFFFFF
        c.drawCircle(540f, 8f, 78f, fill)
        c.drawText("MARKET REPORT", PL, 34f, paint(8f, mint, bold = true))
        val title = paint(22f, white, bold = true)
        c.drawText(fit(title, d.categoryName, 400f), PL, 62f, title)
        c.drawText("Anonymous prices from ${d.institutionCount} institutions", PL, 80f, paint(10f, 0xDDFFFFFF.toInt()))
        c.drawText("Generated", PR, 34f, paint(7.5f, mint, bold = true, align = Paint.Align.RIGHT))
        c.drawText(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")), PR, 48f, paint(9f, white, align = Paint.Align.RIGHT))
        if (demo) c.drawText("DEMO DATA", PR, 80f, paint(9f, 0xFFFDE68A.toInt(), bold = true, align = Paint.Align.RIGHT))

        // Four figure tiles
        val tiles = listOf(
            "AVERAGE PRICE" to kesText(d.averagePrice),
            "MEDIAN PRICE" to kesText(d.medianPrice),
            "LOWEST - HIGHEST" to String.format(Locale.US, "%,.2f - %,.2f", d.minPrice, d.maxPrice),
            "QUANTITY SOLD" to d.totalQuantitySold,
        )
        val tw = (PR - PL - 24f) / 4f
        tiles.forEachIndexed { i, (name, value) ->
            val x = PL + i * (tw + 8f)
            fill.color = tile
            c.drawRoundRect(x, 114f, x + tw, 170f, 8f, 8f, fill)
            c.drawText(name, x + 8f, 130f, paint(6.5f, muted, bold = true))
            val vp = paint(11.5f, deep, bold = true)
            c.drawText(fit(vp, value, tw - 14f), x + 8f, 154f, vp)
        }

        // Price trend chart
        c.drawText("Price trend", PL, 198f, paint(12f, ink, bold = true))
        val top = 212f
        val bottom = 340f
        val left = PL + 44f
        val trend = d.trend
        if (trend.size < 2) {
            c.drawText("Not enough months with 5 or more institutions to show a trend.", PL, 236f, paint(9f, muted))
        } else {
            val lo = trend.minOf { it.averagePrice }
            val hi = trend.maxOf { it.averagePrice }
            val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            for (g in 0..2) {
                val gy = top + (bottom - top) * g / 2f
                c.drawLine(left, gy, PR, gy, line)
            }
            val small = paint(7.5f, muted)
            c.drawText(String.format(Locale.US, "%,.2f", hi), PL, top + 3f, small)
            c.drawText(String.format(Locale.US, "%,.2f", lo), PL, bottom + 3f, small)
            val step = (PR - left - 8f) / (trend.size - 1)
            val pts = trend.mapIndexed { i, t -> (left + 4f + i * step) to (bottom - ((t.averagePrice - lo) / span).toFloat() * (bottom - top)) }
            val area = Path().apply {
                moveTo(pts.first().first, bottom)
                pts.forEach { lineTo(it.first, it.second) }
                lineTo(pts.last().first, bottom)
                close()
            }
            fill.color = 0x260F766E
            c.drawPath(area, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal; strokeWidth = 2f; style = Paint.Style.STROKE }
            for (i in 0 until pts.size - 1) c.drawLine(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second, stroke)
            fill.color = teal
            pts.forEach { c.drawCircle(it.first, it.second, 3f, fill) }
            val mp = paint(7.5f, muted, align = Paint.Align.CENTER)
            trend.forEachIndexed { i, t -> c.drawText(t.month, pts[i].first, bottom + 15f, mp) }
        }

        // Monthly table
        var y = 378f
        c.drawText("Month by month", PL, y, paint(12f, ink, bold = true))
        y += 10f
        fun header(cols: List<Pair<String, Float>>, rightAlign: Set<Int>) {
            fill.color = deep
            c.drawRoundRect(PL, y, PR, y + 22f, 5f, 5f, fill)
            cols.forEachIndexed { i, (name, x) -> c.drawText(name, x, y + 14.5f, paint(8f, white, bold = true, align = if (i in rightAlign) Paint.Align.RIGHT else Paint.Align.LEFT)) }
            y += 22f
        }
        fun row(i: Int, cols: List<Pair<String, Float>>, rightAlign: Set<Int>) {
            if (i % 2 == 1) { fill.color = zebra; c.drawRect(PL, y, PR, y + 18f, fill) }
            cols.forEachIndexed { k, (text, x) -> c.drawText(text, x, y + 12.5f, paint(8.5f, ink, align = if (k in rightAlign) Paint.Align.RIGHT else Paint.Align.LEFT)) }
            y += 18f
        }
        val monthCols = listOf("Month" to PL + 8f, "Average price (KES)" to 400f, "Institutions" to PR - 8f)
        if (trend.isEmpty()) {
            c.drawText("No monthly figures yet.", PL, y + 18f, paint(9f, muted))
            y += 30f
        } else {
            header(monthCols, setOf(1, 2))
            trend.takeLast(5).forEachIndexed { i, t ->
                row(i, listOf(t.month to PL + 8f, String.format(Locale.US, "%,.2f", t.averagePrice) to 400f, "${t.institutionCount}" to PR - 8f), setOf(1, 2))
            }
        }

        // Regional table
        y += 26f
        c.drawText("By county", PL, y, paint(12f, ink, bold = true))
        y += 10f
        if (d.regionalBreakdown.isEmpty()) {
            c.drawText("No county has 5 or more institutions for this category yet.", PL, y + 18f, paint(9f, muted))
            y += 30f
        } else {
            val cols = listOf("County" to PL + 8f, "Institutions" to 270f, "Average" to 340f, "Median" to 410f, "Lowest" to 480f, "Highest" to PR - 8f)
            val right = setOf(1, 2, 3, 4, 5)
            header(cols, right)
            d.regionalBreakdown.take(5).forEachIndexed { i, r ->
                fun n(v: Double) = String.format(Locale.US, "%,.2f", v)
                row(i, listOf(r.region to PL + 8f, "${r.institutionCount}" to 270f, n(r.averagePrice) to 340f, n(r.medianPrice) to 410f, n(r.minPrice) to 480f, n(r.maxPrice) to PR - 8f), right)
            }
        }

        // Constituency table, only when some constituency has enough institutions
        val places = d.constituencyBreakdown.orEmpty()
        if (places.isNotEmpty()) {
            y += 26f
            c.drawText("By constituency", PL, y, paint(12f, ink, bold = true))
            y += 10f
            val cols = listOf("Constituency" to PL + 8f, "County" to 190f, "Institutions" to 300f, "Average" to 380f, "Lowest" to 460f, "Highest" to PR - 8f)
            val right = setOf(2, 3, 4, 5)
            header(cols, right)
            places.take(4).forEachIndexed { i, r ->
                fun n(v: Double) = String.format(Locale.US, "%,.2f", v)
                row(i, listOf(r.region to PL + 8f, (r.county ?: "") to 190f, "${r.institutionCount}" to 300f, n(r.averagePrice) to 380f, n(r.minPrice) to 460f, n(r.maxPrice) to PR - 8f), right)
            }
        }

        // Footer
        c.drawLine(PL, 790f, PR, 790f, line)
        val note = if (demo) "Demo data: made-up numbers for testing. Not real market data." else "Anonymous averages. A figure appears only when 5 or more institutions contribute."
        c.drawText(note, PL, 806f, paint(7.5f, muted))
        c.drawText("Made by KNOW", PR, 806f, paint(8f, teal, bold = true, align = Paint.Align.RIGHT))

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    // Opens the phone's print dialog, which can print or save as a PDF to share
    fun print(context: Context, d: CategoryDetailOut, demo: Boolean) {
        var c: Context? = context
        var activity: Activity? = null
        while (c is ContextWrapper) { if (c is Activity) { activity = c; break }; c = c.baseContext }
        val host = activity ?: return
        val dir = File(context.cacheDir, "market").apply { mkdirs() }
        val file = File(dir, "market_${d.categoryId}.pdf")
        build(file, d, demo)
        val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        val manager = host.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val job = "Market ${d.categoryName}"
        manager.print(job, FilePrintAdapter(file, job), attributes)
    }
}
