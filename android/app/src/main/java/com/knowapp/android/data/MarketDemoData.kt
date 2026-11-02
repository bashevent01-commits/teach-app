package com.knowapp.android.data

import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.CategoryInsightOut
import com.knowapp.android.data.model.RegionBreakdownEntry
import com.knowapp.android.data.model.TrendPointOut

// Made-up market numbers for the demo view only; nothing here comes from a real institution
object MarketDemoData {
    const val FIRST_ID = 9001

    fun isDemoId(categoryId: Int) = categoryId >= FIRST_ID

    private class Seed(val id: Int, val name: String, val institutions: Int, val price: Double, val qty: String, val drift: Double)

    private val seeds = listOf(
        Seed(9001, "Maize flour (2 kg)", 14, 168.0, "18,420", 0.012),
        Seed(9002, "Cooking oil (1 L)", 12, 285.0, "9,310", 0.018),
        Seed(9003, "Sugar (1 kg)", 11, 192.0, "12,775", -0.006),
        Seed(9004, "Exercise books (48 pg)", 9, 42.0, "26,040", 0.009),
        Seed(9005, "Bar soap", 8, 118.0, "7,260", 0.004),
        Seed(9006, "Bottled water (500 ml)", 7, 38.0, "31,500", 0.002),
    )

    private val regions = listOf(
        "Nairobi" to 1.06, "Mombasa" to 1.03, "Kisumu" to 0.98, "Nakuru" to 0.97, "Uasin Gishu" to 0.95,
    )

    private val places = listOf(
        Triple("Westlands", "Nairobi", 1.08), Triple("Nyali", "Mombasa", 1.04), Triple("Embakasi East", "Nairobi", 1.03),
        Triple("Kisumu East", "Kisumu", 0.99), Triple("Nakuru Town East", "Nakuru", 0.97), Triple("Kapseret", "Uasin Gishu", 0.94),
    )

    private val months = listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10")

    private fun r(v: Double) = Math.round(v * 100.0) / 100.0

    fun categories(): List<CategoryInsightOut> = seeds.map {
        CategoryInsightOut(it.id, it.name, it.institutions, r(it.price), r(it.price * 0.99), r(it.price * 0.82), r(it.price * 1.21), it.qty)
    }

    fun detail(categoryId: Int): CategoryDetailOut? {
        val s = seeds.firstOrNull { it.id == categoryId } ?: return null
        val trend = months.mapIndexed { i, m ->
            val price = s.price * (1 + s.drift * (i - months.lastIndex))
            TrendPointOut(m, r(price), maxOf(5, s.institutions - (months.lastIndex - i)))
        }
        val breakdown = regions.take(if (s.institutions >= 10) 5 else 3).mapIndexed { i, (name, factor) ->
            val avg = s.price * factor
            RegionBreakdownEntry(name, null, maxOf(5, s.institutions / 2 - i / 2), r(avg), r(avg * 0.99), r(avg * 0.85), r(avg * 1.18))
        }
        val constituencies = places.take(if (s.institutions >= 10) 6 else 4).mapIndexed { i, (name, county, factor) ->
            val avg = s.price * factor
            RegionBreakdownEntry(name, county, maxOf(5, s.institutions / 3 - i / 3), r(avg), r(avg * 0.99), r(avg * 0.88), r(avg * 1.14))
        }
        return CategoryDetailOut(s.id, s.name, s.institutions, r(s.price), r(s.price * 0.99), r(s.price * 0.82), r(s.price * 1.21), s.qty, breakdown, trend, constituencies)
    }
}
