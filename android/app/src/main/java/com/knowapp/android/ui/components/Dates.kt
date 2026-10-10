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


// "Active today", "Active 3 Oct", "Never active": when a person or institution last did something
fun lastSeen(iso: String?): String {
    if (iso == null) return "Never active"
    val label = relativeDay(iso)
    return if (label == "Today" || label == "Yesterday") "Active ${label.lowercase()}" else "Active $label"
}

fun daysSince(iso: String?): Long? = iso?.let { java.time.temporal.ChronoUnit.DAYS.between(dateOf(it), LocalDate.now()) }

private val AMBIGUOUS = setOf('0', 'O', 'o', '1', 'l', 'I')
private const val PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

// A temporary password that is easy to read out over the phone: no look-alike characters, always a letter and a digit
fun generatePassword(length: Int = 10): String {
    val random = java.security.SecureRandom()
    while (true) {
        val candidate = (1..length).map { PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)] }.joinToString("")
        if (candidate.any { it.isDigit() } && candidate.any { it.isLetter() } && candidate.none { it in AMBIGUOUS }) return candidate
    }
}
