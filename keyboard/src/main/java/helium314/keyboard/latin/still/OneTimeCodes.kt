// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

/** Parsing is deliberately bounded and never stores the message or sender. */
object OneTimeCodes {
    const val LIFETIME_MS = 30_000L
    const val SMS_ENABLED_PREF = "still_sms_codes_enabled"
    private val token = Regex("(?<![\\p{L}\\p{N}_/-])[A-Za-z0-9]{4,8}(?![\\p{L}\\p{N}_/-])")
    private val context = Regex("\\b(code|otp|pin|verification|verify|security|login|authentication|one[ -]time)\\b", RegexOption.IGNORE_CASE)
    private val wholeCode = Regex("[A-Za-z0-9]{4,8}")

    fun fromClipboard(text: String): String? = text.takeIf { it.length <= 16 }?.trim()?.takeIf {
        wholeCode.matches(it) && (it.any(Char::isDigit) || it.all { c -> c in 'A'..'Z' })
    }

    fun fromMessage(text: String): List<String> {
        val bounded = text.take(4096)
        val standalone = fromClipboard(bounded)
        if (standalone != null) return listOf(standalone)
        if (!context.containsMatchIn(bounded)) return emptyList()
        return token.findAll(bounded).map { it.value }.filter {
            it.any(Char::isDigit) || it.all { c -> c in 'A'..'Z' }
        }.distinct().take(3).toList()
    }

    fun isRecent(timestamp: Long, now: Long) = timestamp > 0 && now >= timestamp && now - timestamp < LIFETIME_MS
}

/** Uses a monotonic receive time; new messages cannot extend an old code's lifetime. */
class RecentCodeStore {
    data class Entry(val code: String, val receivedAt: Long)
    private val entries = ArrayList<Entry>()

    fun add(codes: List<String>, now: Long) {
        current(now)
        for (code in codes) {
            entries.removeAll { it.code == code }
            entries.add(0, Entry(code, now))
        }
        while (entries.size > 3) entries.removeAt(entries.lastIndex)
    }

    fun current(now: Long): List<Entry> {
        entries.removeAll { now < it.receivedAt || now - it.receivedAt >= OneTimeCodes.LIFETIME_MS }
        return entries.toList()
    }

    fun clear() = entries.clear()
}
