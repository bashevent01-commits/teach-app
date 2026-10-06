package com.knowapp.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

// Picks an icon from the category name so a list reads at a glance; falls back to the money-in or money-out arrow
fun categoryIcon(category: String?, type: String): ImageVector {
    val c = (category ?: "").lowercase()
    return when {
        listOf("fee", "tuition", "school", "exam").any { c.contains(it) } -> Icons.Outlined.School
        listOf("sale", "sold", "customer").any { c.contains(it) } -> Icons.Outlined.ShoppingCart
        listOf("rent", "lease", "mortgage").any { c.contains(it) } -> Icons.Outlined.Home
        listOf("salar", "wage", "payroll", "staff").any { c.contains(it) } -> Icons.Outlined.Groups
        listOf("transport", "fuel", "fare", "travel").any { c.contains(it) } -> Icons.Outlined.DirectionsBus
        listOf("utilit", "electric", "power", "water", "kplc").any { c.contains(it) } -> Icons.Outlined.Bolt
        listOf("suppl", "stock", "purchase", "inventory").any { c.contains(it) } -> Icons.Outlined.Inventory2
        listOf("donat", "gift", "charity").any { c.contains(it) } -> Icons.Outlined.Favorite
        listOf("food", "lunch", "meal", "catering").any { c.contains(it) } -> Icons.Outlined.Restaurant
        listOf("repair", "maint", "service").any { c.contains(it) } -> Icons.Outlined.Build
        type == "income" -> Icons.Outlined.ArrowDownward
        else -> Icons.Outlined.ArrowUpward
    }
}
