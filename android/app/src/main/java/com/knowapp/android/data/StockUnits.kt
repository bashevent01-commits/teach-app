package com.knowapp.android.data

import java.math.BigDecimal
import java.math.RoundingMode

// Units a business can count stock in; the key is what the server stores
val STOCK_UNITS: List<Pair<String, String>> = listOf(
    "pieces" to "Pieces", "kg" to "Kilograms (kg)", "g" to "Grams (g)", "mg" to "Milligrams (mg)", "litres" to "Litres (L)", "ml" to "Millilitres (ml)",
    "dozen" to "Dozen", "packets" to "Packets", "bottles" to "Bottles", "cartons" to "Cartons", "boxes" to "Boxes",
    "bags" to "Bags", "sacks" to "Sacks", "crates" to "Crates", "trays" to "Trays", "bundles" to "Bundles",
    "pairs" to "Pairs", "metres" to "Metres (m)", "rolls" to "Rolls", "tins" to "Tins", "sachets" to "Sachets",
    "plates" to "Plates", "cups" to "Cups", "loaves" to "Loaves", "bales" to "Bales", "gallons" to "Gallons", "tonnes" to "Tonnes",
)

const val OTHER_UNIT = "Other (type your own)"

private val SINGULAR = mapOf(
    "pieces" to "piece", "litres" to "litre", "packets" to "packet", "bottles" to "bottle", "cartons" to "carton", "boxes" to "box",
    "bags" to "bag", "sacks" to "sack", "crates" to "crate", "trays" to "tray", "bundles" to "bundle", "pairs" to "pair",
    "metres" to "metre", "rolls" to "roll", "tins" to "tin", "sachets" to "sachet", "plates" to "plate", "cups" to "cup",
    "loaves" to "loaf", "bales" to "bale", "gallons" to "gallon", "tonnes" to "tonne",
)

// The label shown in a dropdown for a stored unit; an unknown unit is a custom one
fun unitChoice(key: String?): String = STOCK_UNITS.firstOrNull { it.first == key }?.second ?: OTHER_UNIT

fun unitKey(label: String): String? = STOCK_UNITS.firstOrNull { it.second == label }?.first

// "12.5 kg", "1 piece", "30 pieces"
fun qtyText(raw: String?, unit: String?): String {
    val n = raw?.toBigDecimalOrNull()?.stripTrailingZeros()
    val shown = n?.toPlainString() ?: (raw ?: "")
    val u = unit?.ifBlank { null } ?: "pieces"
    val word = if (n != null && n.compareTo(BigDecimal.ONE) == 0) SINGULAR[u] ?: u else u
    return "$shown $word"
}

// Measures that can be swapped for each other, each with its size in the family's base unit
private val FAMILIES: Map<String, Pair<String, BigDecimal>> = mapOf(
    "mg" to ("weight" to BigDecimal("0.001")), "g" to ("weight" to BigDecimal.ONE), "kg" to ("weight" to BigDecimal("1000")), "tonnes" to ("weight" to BigDecimal("1000000")),
    "ml" to ("volume" to BigDecimal.ONE), "litres" to ("volume" to BigDecimal("1000")), "gallons" to ("volume" to BigDecimal("3785.41")),
    "pieces" to ("count" to BigDecimal.ONE), "dozen" to ("count" to BigDecimal("12")), "pairs" to ("count" to BigDecimal("2")),
)

// The measures a sale of this item can be entered in: its own, plus any it converts to
fun compatibleUnits(unit: String): List<String> {
    val family = FAMILIES[unit]?.first ?: return listOf(unit)
    return STOCK_UNITS.map { it.first }.filter { FAMILIES[it]?.first == family }
}

// The quantity expressed in the item's own unit, to two decimals, or null when the measures do not convert
fun convertQty(qty: BigDecimal, from: String, to: String): BigDecimal? {
    if (from == to) return qty.setScale(2, RoundingMode.HALF_UP)
    val f = FAMILIES[from] ?: return null
    val t = FAMILIES[to] ?: return null
    if (f.first != t.first) return null
    return qty.multiply(f.second).divide(t.second, 2, RoundingMode.HALF_UP)
}
