package com.knowapp.android.data

import java.math.BigDecimal

// Units a business can count stock in; the key is what the server stores
val STOCK_UNITS: List<Pair<String, String>> = listOf(
    "pieces" to "Pieces", "kg" to "Kilograms (kg)", "g" to "Grams (g)", "litres" to "Litres (L)", "ml" to "Millilitres (ml)",
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

fun unitLabel(key: String?): String = STOCK_UNITS.firstOrNull { it.first == key }?.second ?: (key ?: "Pieces")

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
