package app.farmsy.android.core

import java.util.Calendar
import java.util.TimeZone

/// Quick-filter predicates, ported verbatim from iOS FarmFilters.swift (itself a
/// port of the web) so all three clients filter the same farms.
object FarmFilters {

    // 1 = Sunday … 7 = Saturday (Java Calendar) → Monday-indexed 0..6.
    private val dayMon = mapOf(1 to 6, 2 to 0, 3 to 1, 4 to 2, 5 to 3, 6 to 4, 7 to 5)
    /// Day names to Monday-indexed weekdays (0 = Monday … 6 = Sunday), lowercased.
    ///
    /// OSM's own abbreviations, and then Dutch, because half our readable hours
    /// are not in OSM's format at all: 2,762 of the 5,688 farms that have
    /// opening hours carry them as `maandag: 09:00-17:00`, imported from sources
    /// that wrote them the way a Dutch person would.
    ///
    /// Until this table grew, every one of those parsed to "not open", on every
    /// day, forever — which quietly took half the hours data out of the free
    /// open-today filter and out of the paid open-right-now one. A member was
    /// paying for an answer missing half its data, and the failure was invisible
    /// because it removes farms rather than adding wrong ones. The web fixed
    /// this; the apps never got the fix. This is that fix. Mirrors web DAY_JS.
    private val dayTok = mapOf(
        // OSM
        "mo" to 0, "tu" to 1, "we" to 2, "th" to 3, "fr" to 4, "sa" to 5, "su" to 6,
        // Dutch, full and short
        "maandag" to 0, "dinsdag" to 1, "woensdag" to 2, "donderdag" to 3,
        "vrijdag" to 4, "zaterdag" to 5, "zondag" to 6,
        "ma" to 0, "di" to 1, "wo" to 2, "do" to 3, "vr" to 4, "za" to 5, "zo" to 6,
        // French
        "lundi" to 0, "mardi" to 1, "mercredi" to 2, "jeudi" to 3,
        "vendredi" to 4, "samedi" to 5, "dimanche" to 6,
        // German
        "montag" to 0, "dienstag" to 1, "mittwoch" to 2, "donnerstag" to 3,
        "freitag" to 4, "samstag" to 5, "sonntag" to 6,
    )

    /// A day token, as written, reduced to something [dayTok] can answer.
    ///
    /// Strips the trailing colon of `maandag:`, takes the first word of
    /// `maandag: 24 uur geopend`, and lowercases. Returns an empty string when
    /// there is no word to take, so a caller can tell "no day here" from "a day
    /// I do not recognise" — only one of those is a bug. Mirrors web dayKey.
    private fun dayKey(raw: String): String =
        raw.trim().split(' ', '\t', ':').firstOrNull().orEmpty()
            .lowercase().trimEnd('.', ',', ':')
    /// What counts as "shut" in a segment.
    ///
    /// OSM writes `Su off`. Our data does not: it was imported from sources that
    /// wrote `zondag gesloten`, `geschlossen`, `fermé`, or plain `closed`.
    /// Matching only `off` did not merely miss those — it read them as OPEN,
    /// because the segment still names a weekday and nothing marked it shut. A
    /// farm whose own hours say it is closed on Sunday was reported open on
    /// Sunday, which is the locked gate this whole file exists to avoid.
    /// Mirrors web OFF_RE.
    private val offRegex = Regex("""\b(off|gesloten|geschlossen|ferm[eé]|closed)\b""", RegexOption.IGNORE_CASE)
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
                val a = dayTok[dayKey(parts[0])] ?: continue
                val b = dayTok[dayKey(parts[1])] ?: continue
                if (a <= b) {
                    for (d in a..b) out += d
                } else {
                    for (d in a..6) out += d
                    for (d in 0..b) out += d
                }
            } else {
                dayTok[dayKey(g)]?.let { out += it }
            }
        }
        return out
    }

    private val automaatWords = listOf(
        "automaat", "automat", "melktap", "eierautomaat", "kaasautomaat",
        "aardappelautomaat", "vending", "zelfbediening", "self-service", "selfservice",
    )

    // ── Open, shut, or unknown (R6) ──────────────────────────────────────────

    /// Three answers, not two.
    ///
    /// [isOpenOnDay] returns false both for a farm shut on Tuesday and for one
    /// whose hours nobody ever recorded. On a map that is tolerable. On a route
    /// it is not: 2,745 of 8,433 farms carry no opening_hours at all, and
    /// showing those as closed hides a third of the country from someone
    /// planning a Saturday. Calling them open is worse — that is how somebody
    /// drives forty minutes to a locked gate. Mirrors web DayStatus.
    enum class DayStatus { OPEN, CLOSED, UNKNOWN }

    /// Open, shut or unknown on a given weekday. Mirrors web statusOnDay.
    fun statusOnDay(openingHours: String?, dayMonIndex: Int): DayStatus {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return DayStatus.UNKNOWN
        if (raw == "24/7") return DayStatus.OPEN
        if (isOpenOnDay(raw, dayMonIndex)) return DayStatus.OPEN

        // Nothing matched, which is two situations. A string naming weekdays and
        // not this one is a farm that is shut. A string we could not read a
        // weekday out of tells us nothing, and must not be dressed up as a fact.
        return if (mentionsAWeekday(raw)) DayStatus.CLOSED else DayStatus.UNKNOWN
    }

    private fun mentionsAWeekday(raw: String): Boolean =
        raw.split('\n', ';').any { segment ->
            val dayPart = dayPartOf(segment.trim())
            dayPart.isNotEmpty() && daysOf(dayPart).isNotEmpty()
        }

    /// Every open window on a given weekday, as `[from, to)` minutes past
    /// midnight.
    ///
    /// Separate from [windowsOf], which reads one segment and does not know what
    /// day it belongs to. A farm can carry different hours per day —
    /// `Mo-Fr 09:00-17:00; Sa 09:00-13:00` — and a Saturday trip must not be
    /// measured against the weekday row.
    ///
    /// An empty result does not mean shut. `Mo-Fr` with no times is a farm we
    /// know opens on Monday and whose hours nobody recorded; the caller has to
    /// tell those apart, which is why this returns windows and not a verdict.
    private fun windowsOnDay(raw: String, dayMonIndex: Int): List<Pair<Int, Int>> =
        raw.split('\n', ';').flatMap { segment ->
            val s = segment.trim()
            if (s.isEmpty() || offRegex.containsMatchIn(s)) return@flatMap emptyList()
            val dayPart = dayPartOf(s)
            if (dayPart.isEmpty() || !daysOf(dayPart).contains(dayMonIndex)) return@flatMap emptyList()
            windowsOf(s)
        }

    /// Open, shut or unknown on a given weekday, **between two times** (R6).
    ///
    /// [statusOnDay] answers "does this farm open at all on Saturday". That is
    /// the wrong question for a route: a shop open Saturday 09:00-13:00 is shut
    /// when you drive past at four, and a planner that lists it has sent someone
    /// to a locked gate just as surely as one with no hours at all.
    ///
    /// Times are minutes past midnight and the window is half-open, `[from, to)`.
    /// The same value twice asks about a single moment.
    ///
    /// The answer this exists to avoid: a farm whose day we know and whose hours
    /// we do not — `Mo-Fr`, no times — is UNKNOWN, never open. Open there is a
    /// guess dressed as a fact; closed would hide a farm that may be the one
    /// they wanted. Mirrors web statusOnDayBetween.
    fun statusOnDayBetween(
        openingHours: String?,
        dayMonIndex: Int,
        fromMinutes: Int,
        toMinutes: Int,
    ): DayStatus {
        val raw = openingHours?.trim().orEmpty()
        if (raw.isEmpty()) return DayStatus.UNKNOWN
        if (raw == "24/7") return DayStatus.OPEN

        // The day comes first: a farm shut on Saturday is shut at every hour of
        // it, and one whose hours are unreadable stays unreadable.
        val day = statusOnDay(raw, dayMonIndex)
        if (day != DayStatus.OPEN) return day

        val windows = windowsOnDay(raw, dayMonIndex)
        if (windows.isEmpty()) return DayStatus.UNKNOWN

        // A zero-length ask is a moment, not a span, so give it a minute of width
        // rather than answering "no overlap" for every farm on the map.
        val from = minOf(fromMinutes, toMinutes)
        val upper = maxOf(fromMinutes, toMinutes)
        val end = if (upper == from) from + 1 else upper

        return if (windows.any { (openFrom, openTo) -> openFrom < end && from < openTo })
            DayStatus.OPEN else DayStatus.CLOSED
    }

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
