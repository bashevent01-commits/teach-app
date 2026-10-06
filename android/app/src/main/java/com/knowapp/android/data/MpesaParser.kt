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

    private val bankRefRe = Regex(
        "(?:ref(?:erence)?|txn|trx|transaction)\\s*(?:no\\.?|number|id)?\\s*[:#\\-]?\\s*([A-Z0-9]{6,20})",
        RegexOption.IGNORE_CASE,
    )
    private val bankIncomingRe = Regex("credited|deposit|received|credit alert")
    private val bankOutgoingRe = Regex("debited|withdraw|paid to|transfer to|debit alert")

    // Bank SMS alerts differ by bank, so this looks for the common pieces: an amount, a reference and the other party
    fun parseBank(raw: String): Parsed {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return Parsed(null, null, null, null)
        val amount = Regex("(?:KES|Ksh|Kshs)\\.?\\s?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(",", "")
        val labelled = bankRefRe.find(text)?.groupValues?.get(1)?.uppercase()
        val loose = Regex("\\b([A-Z0-9]{8,16})\\b").findAll(text).map { it.groupValues[1] }.firstOrNull { c -> c.any { it.isLetter() } && c.any { it.isDigit() } }
        val party = partyRe.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.take(150)
        val lower = text.lowercase()
        val incoming = when {
            bankIncomingRe.containsMatchIn(lower) -> true
            bankOutgoingRe.containsMatchIn(lower) -> false
            else -> null
        }
        return Parsed(labelled ?: loose, amount, party, incoming)
    }
}
