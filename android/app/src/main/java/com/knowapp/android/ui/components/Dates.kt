package com.knowapp.android.ui.components

import com.knowapp.android.ui.statements.dateOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun relativeDay(iso: String): String {
    val date = dateOf(iso)
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
