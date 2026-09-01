package app.farmsy.android.core

import java.util.Calendar
import java.util.TimeZone

/// Quick-filter predicates, ported verbatim from iOS FarmFilters.swift (itself a
/// port of the web) so all three clients filter the same farms.
object FarmFilters {

    // 1 = Sunday … 7 = Saturday (Java Calendar) → Monday-indexed 0..6.
    private val dayMon = mapOf(1 to 6, 2 to 0, 3 to 1, 4 to 2, 5 to 3, 6 to 4, 7 to 5)
    private val dayTok = mapOf("Mo" to 0, "Tu" to 1, "We" to 2, "Th" to 3, "Fr" to 4, "Sa" to 5, "Su" to 6)
    private val offRegex = Regex("""\boff\b""", RegexOption.IGNORE_CASE)
    private val timeToken = Regex("""\s+\d{1,2}:\d{2}""")
    private val windowRegex = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")

    // The Pro time filters (and isOpenToday) read the wall clock in Amsterdam, NOT on
    // the device — every farm is in NL/BE, so a visitor in another timezone means open
    // where the farms are. Reading the device clock had the free "open today" filter
    // and the paid open-now filter disagree for someone outside CET. Mirrors web a46ab4b.
    private val amsterdam: TimeZone = TimeZone.getTimeZone("Europe/Amsterdam")

    private fun amsterdamCalendar(): Calendar = Calendar.getInstance(amsterdam)

    /// The Amsterdam weekday, Mon-based (0 = Monday … 6 = Sunday).
    private fun todayInAmsterdam(cal: Calendar = amsterdamCalendar()): Int =
        dayMon[cal.get(Calendar.DAY_OF_WEEK)] ?: 0

    /// True when the OSM opening_hours string has today's weekday open.
    fun isOpenToday(openingHours: String?): Boolean {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (raw == "24/7") return true

        // Amsterdam weekday, not the device clock (see note above). Mirrors web a46ab4b.
        val todayMon = todayInAmsterdam()

        // A later `off` overrides an earlier open rule — "Mo-Su 09:00-17:00; Su off"
        // is shut on Sunday. The loop below can only ever ADD an open day and it
        // short-circuits on the first match, so a closure has to be collected and
        // subtracted UP FRONT; the `continue` alone merely skips the off segment,
        // which lets the broad rule above it win on exactly the day it said closed.
        // Mirrors web src/lib/opening-hours.ts closedDays().
        if (todayMon in closedDays(raw)) return false

        for (segment in raw.split('\n', ';')) {
            val s = segment.trim()
            if (s.isEmpty()) continue
            if (offRegex.containsMatchIn(s)) continue

            val dayPart = dayPartOf(s)
            if (dayPart.isEmpty()) continue
            if (todayMon in daysOf(dayPart)) return true
        }
        return false
    }

    /// The day-part of a segment — everything before the first HH:MM token, trimmed.
    private fun dayPartOf(s: String): String =
        (timeToken.find(s)?.let { s.substring(0, it.range.first) } ?: s).trim()

    /// Every HH:MM-HH:MM window in a segment, as minutes past midnight. `00:00` as a
    /// closing time means midnight at the END of the day (09:00-00:00 open at 21:00).
    /// Mirrors web windowsOf().
    private fun windowsOf(segment: String): List<Pair<Int, Int>> =
        windowRegex.findAll(segment).map { m ->
            val from = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            var to = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (to == 0) to = 24 * 60
            from to to
        }.toList()

    /// Open at this exact minute, in Amsterdam time (the Pro "open right now" filter).
    /// Distinct from `isOpenToday`, which only asks whether the day is one the farm
    /// opens at all. Mirrors web isOpenNow().
    fun isOpenNow(openingHours: String?): Boolean {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (raw == "24/7") return true

        val cal = amsterdamCalendar()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = todayInAmsterdam(cal)

        if (today in closedDays(raw)) return false

        for (segment in raw.split('\n', ';')) {
            val s = segment.trim()
            if (s.isEmpty() || offRegex.containsMatchIn(s)) continue
            val dayPart = dayPartOf(s)
            if (dayPart.isEmpty() || today !in daysOf(dayPart)) continue

            // A day named with no times at all is treated as open — some records say
            // "Mo-Sa" and nothing more, and refusing those would hide real farms.
            val windows = windowsOf(s)
            if (windows.isEmpty()) return true
            if (windows.any { minutes >= it.first && minutes < it.second }) return true
        }
        return false
    }

    /// Open on a given weekday, Mon-based (0 = Monday … 6 = Sunday) — the Pro "open
    /// Saturday/Sunday" filters. Mirrors web isOpenOnDay().
    fun isOpenOnDay(openingHours: String?, dayMonIndex: Int): Boolean {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (raw == "24/7") return true
        if (dayMonIndex in closedDays(raw)) return false
        for (segment in raw.split('\n', ';')) {
            val s = segment.trim()
            if (s.isEmpty() || offRegex.containsMatchIn(s)) continue
            val dayPart = dayPartOf(s)
            if (dayPart.isNotEmpty() && dayMonIndex in daysOf(dayPart)) return true
        }
        return false
    }

    /// Days a record explicitly closes — the `Su` in "…; Su off" — as Mon-indexed
    /// weekdays. `isOpenToday` subtracts these before it looks for an open day,
    /// because in OSM notation a later rule overrides an earlier one. Unknown
    /// tokens (e.g. `PH`) resolve to nothing and close no day. Mirrors web
    /// src/lib/opening-hours.ts closedDays().
    private fun closedDays(raw: String): Set<Int> {
        val out = mutableSetOf<Int>()
        for (segment in raw.split('\n', ';')) {
            val s = segment.trim()
            if (s.isEmpty() || !offRegex.containsMatchIn(s)) continue
            val dayPart = offRegex.replace(s, "").trim()
            if (dayPart.isEmpty()) continue
            out += daysOf(dayPart)
        }
        return out
    }

    /// The Mon-indexed weekdays a day-part covers: "Mo-Fr" (range, wrap-aware),
    /// "Sa,Su" (comma list), "We" (single). Mirrors web daysOf().
    private fun daysOf(dayPart: String): Set<Int> {
        val out = mutableSetOf<Int>()
        for (group in dayPart.split(',')) {
            val g = group.trim()
            if (g.isEmpty()) continue
            if (g.contains('-')) {
                val parts = g.split('-')
                if (parts.size != 2) continue
                val a = dayTok[parts[0].trim()] ?: continue
                val b = dayTok[parts[1].trim()] ?: continue
                if (a <= b) {
                    for (d in a..b) out += d
                } else {
                    for (d in a..6) out += d
                    for (d in 0..b) out += d
                }
            } else {
                dayTok[g]?.let { out += it }
            }
        }
        return out
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
