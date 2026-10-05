package com.knowapp.android.data

// Pulls the transaction code, amount and other party out of a pasted M-Pesa confirmation SMS
object MpesaParser {
    data class Parsed(val code: String?, val amount: String?, val party: String?, val incoming: Boolean?)

    private val codeRe = Regex("\\b([A-Z0-9]{10})\\b")
    private val amountRe = Regex("Ksh\\.?\\s?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val partyRe = Regex(
        "(?:received from|sent to|paid to|\\bfrom\\b)\\s+(.+?)(?=\\s+\\d{9,12}\\b|\\s+on\\s+\\d|\\.\\s|\\.?\\s+on\\s)",
        RegexOption.IGNORE_CASE,
    )
    private val outgoingRe = Regex("sent to|paid to|withdraw|you bought")

    fun parse(raw: String): Parsed {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return Parsed(null, null, null, null)
        val code = codeRe.findAll(text).map { it.groupValues[1] }.firstOrNull { c -> c.any { it.isLetter() } && c.any { it.isDigit() } }
        val amount = amountRe.find(text)?.groupValues?.get(1)?.replace(",", "")
        val party = partyRe.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.take(150)
        val lower = text.lowercase()
        val incoming = when {
            lower.contains("received") -> true
            outgoingRe.containsMatchIn(lower) -> false
            else -> null
        }
        return Parsed(code, amount, party, incoming)
    }
}
