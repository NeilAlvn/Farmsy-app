package app.farmsy.android.core

import java.util.Calendar

/// Quick-filter predicates, ported verbatim from iOS FarmFilters.swift (itself a
/// port of the web) so all three clients filter the same farms.
object FarmFilters {

    // 1 = Sunday … 7 = Saturday (Java Calendar) → Monday-indexed 0..6.
    private val dayMon = mapOf(1 to 6, 2 to 0, 3 to 1, 4 to 2, 5 to 3, 6 to 4, 7 to 5)
    private val dayTok = mapOf("Mo" to 0, "Tu" to 1, "We" to 2, "Th" to 3, "Fr" to 4, "Sa" to 5, "Su" to 6)

    /// True when the OSM opening_hours string has today's weekday open.
    fun isOpenToday(openingHours: String?): Boolean {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (raw == "24/7") return true

        val todayMon = dayMon[Calendar.getInstance().get(Calendar.DAY_OF_WEEK)] ?: return false
        val timeToken = Regex("""\s+\d{1,2}:\d{2}""")

        for (segment in raw.split('\n', ';')) {
            val s = segment.trim()
            if (s.isEmpty()) continue
            if (Regex("""\boff\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)) continue

            val dayPart = timeToken.find(s)?.let { s.substring(0, it.range.first) } ?: s
            val trimmed = dayPart.trim()
            if (trimmed.isEmpty()) continue

            for (group in trimmed.split(',')) {
                val g = group.trim()
                if (g.contains('-')) {
                    val parts = g.split('-')
                    if (parts.size != 2) continue
                    val a = dayTok[parts[0]] ?: continue
                    val b = dayTok[parts[1]] ?: continue
                    if (a <= b) {
                        if (todayMon in a..b) return true
                    } else {
                        if (todayMon >= a || todayMon <= b) return true
                    }
                } else {
                    if (dayTok[g] == todayMon) return true
                }
            }
        }
        return false
    }

    private val automaatWords = listOf(
        "automaat", "automat", "melktap", "eierautomaat", "kaasautomaat",
        "aardappelautomaat", "vending", "zelfbediening", "self-service", "selfservice",
    )

    fun looksLikeAutomaat(text: String?, openingHours: String? = null): Boolean {
        val t = (text ?: "").lowercase()
        if (automaatWords.any { t.contains(it) }) return true
        val h = (openingHours ?: "").lowercase().replace(" ", "")
        return h.contains("24/7") || h.contains("24uur") || h.contains("00:00-24:00")
    }

    private val zelfplukWords = listOf(
        "zelfpluk", "zelf plukken", "pluktuin", "plukboerderij", "zelfoogst",
        "pick your own", "u-pick", "self-pick", "cueillette",
    )

    fun looksLikeZelfpluk(text: String?): Boolean {
        val t = (text ?: "").lowercase()
        return zelfplukWords.any { t.contains(it) }
    }
}
