package com.knowapp.android.data

import com.knowapp.android.data.model.MemberOut
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val MEMBER_PERIODS = listOf("weekly" to "Weekly", "monthly" to "Monthly", "quarterly" to "Every 3 months", "yearly" to "Yearly")

fun periodLabel(key: String): String = MEMBER_PERIODS.firstOrNull { it.first == key }?.second ?: key

fun niceDay(iso: String?): String = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(iso ?: "")

// "Due tomorrow", "Overdue by 3 days" and so on
fun dueLabel(m: MemberOut): String = when {
    !m.isActive -> "Paused"
    m.daysToDue < 0 -> "Overdue by ${-m.daysToDue} day" + if (m.daysToDue == -1) "" else "s"
    m.daysToDue == 0 -> "Due today"
    m.daysToDue == 1 -> "Due tomorrow"
    else -> "Next payment ${niceDay(m.nextDue)}"
}
