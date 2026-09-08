import Foundation

// Quick-filter predicates, ported verbatim from the web (src/lib/opening-hours.ts
// and src/app/map/mapSearch.ts) so the app filters the same farms the site does.
enum FarmFilters {

    // MARK: - Open today

    private static let dayMon: [Int: Int] = [1: 0, 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 0: 6]
    /// Day names to `Date` weekday values (1 = Monday … 0 = Sunday), lowercased.
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
    /// this; the apps never got the fix. This is that fix.
    ///
    /// French and German too: Belgium is bilingual and the German import shares
    /// the same shape. Mirrors web `DAY_JS`.
    private static let dayJS: [String: Int] = [
        // OSM
        "mo": 1, "tu": 2, "we": 3, "th": 4, "fr": 5, "sa": 6, "su": 0,
        // Dutch, full and short
        "maandag": 1, "dinsdag": 2, "woensdag": 3, "donderdag": 4, "vrijdag": 5, "zaterdag": 6, "zondag": 0,
        "ma": 1, "di": 2, "wo": 3, "do": 4, "vr": 5, "za": 6, "zo": 0,
        // French
        "lundi": 1, "mardi": 2, "mercredi": 3, "jeudi": 4, "vendredi": 5, "samedi": 6, "dimanche": 0,
        // German
        "montag": 1, "dienstag": 2, "mittwoch": 3, "donnerstag": 4, "freitag": 5, "samstag": 6, "sonntag": 0,
    ]

    /// A day token, as written, reduced to something `dayJS` can answer.
    ///
    /// Strips the trailing colon of `maandag:`, takes the first word of
    /// `maandag: 24 uur geopend`, and lowercases. Returns an empty string when
    /// there is no word to take, so a caller can tell "no day here" from "a day
    /// I do not recognise" — only one of those is a bug. Mirrors web `dayKey`.
    private static func dayKey(_ raw: String) -> String {
        let first = raw.trimmingCharacters(in: .whitespaces)
            .components(separatedBy: CharacterSet(charactersIn: " \t:")).first ?? ""
        return first.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".,:"))
    }

    /// True when the OSM opening_hours string has today's weekday open. Day-based
    /// (not time-of-day), matching the web's isOpenToday.
    static func isOpenToday(_ openingHours: String?) -> Bool {
        guard let raw = openingHours?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty
        else { return false }
        if raw == "24/7" { return true }

        // The weekday is read in Amsterdam, NOT on the device — every farm is in NL/BE,
        // so "open today" means today where the farms are. Reading the device clock had
        // the free filter and the paid open-now filter disagree for a visitor outside
        // CET (Tokyo Monday morning = Sunday night in Amsterdam). Mirrors web a46ab4b.
        let todayMon = todayInAmsterdam()

        // A later `off` overrides an earlier open rule — "Mo-Su 09:00-17:00; Su off"
        // is shut on Sunday. The loop below can only ever ADD an open day and it
        // short-circuits on the first match, so a closure has to be collected and
        // subtracted UP FRONT; the `continue` alone merely skips the off segment,
        // which lets the broad rule above it win on exactly the day it said closed.
        // Mirrors web src/lib/opening-hours.ts closedDays().
        if closedDays(raw).contains(todayMon) { return false }

        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let s = segment.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { continue }
            if s.range(of: "\\boff\\b", options: [.regularExpression, .caseInsensitive]) != nil { continue }

            // Day part is everything before the first HH:MM token.
            let dayPart = s.range(of: "\\s+\\d{1,2}:\\d{2}", options: .regularExpression)
                .map { String(s[s.startIndex..<$0.lowerBound]) } ?? s
            let trimmedDayPart = dayPart.trimmingCharacters(in: .whitespaces)
            if trimmedDayPart.isEmpty { continue }

            for group in trimmedDayPart.components(separatedBy: ",") {
                let g = group.trimmingCharacters(in: .whitespaces)
                if g.contains("-") {
                    let parts = g.components(separatedBy: "-")
                    guard parts.count == 2,
                          let ja = dayJS[dayKey(parts[0])], let jb = dayJS[dayKey(parts[1])],
                          let startMon = dayMon[ja], let endMon = dayMon[jb] else { continue }
                    if startMon <= endMon {
                        if todayMon >= startMon && todayMon <= endMon { return true }
                    } else {
                        if todayMon >= startMon || todayMon <= endMon { return true }
                    }
                } else if let js = dayJS[dayKey(g)], dayMon[js] == todayMon {
                    return true
                }
            }
        }
        return false
    }

    // MARK: - Amsterdam clock (Pro time filters + isOpenToday)

    private static let amsterdam = TimeZone(identifier: "Europe/Amsterdam") ?? .current

    private static func amsterdamCalendar() -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = amsterdam
        return cal
    }

    /// The Amsterdam weekday, Mon-based (0 = Monday … 6 = Sunday). Mirrors web
    /// `todayInAmsterdam`.
    private static func todayInAmsterdam(_ date: Date = Date()) -> Int {
        let js = amsterdamCalendar().component(.weekday, from: date) - 1  // 0=Sun … 6=Sat
        return dayMon[js] ?? 0
    }

    /// The day-part of a segment — everything before the first HH:MM token, trimmed.
    private static func dayPartOf(_ s: String) -> String {
        let dayPart = s.range(of: "\\s+\\d{1,2}:\\d{2}", options: .regularExpression)
            .map { String(s[s.startIndex..<$0.lowerBound]) } ?? s
        return dayPart.trimmingCharacters(in: .whitespaces)
    }

    /// Every HH:MM-HH:MM window in a segment, as minutes past midnight. `00:00` as a
    /// closing time means midnight at the END of the day (09:00-00:00 open at 21:00).
    /// Mirrors web `windowsOf`.
    private static func windowsOf(_ segment: String) -> [(from: Int, to: Int)] {
        guard let re = try? NSRegularExpression(pattern: "(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
        else { return [] }
        let ns = segment as NSString
        var out: [(Int, Int)] = []
        for m in re.matches(in: segment, range: NSRange(location: 0, length: ns.length)) {
            let from = (Int(ns.substring(with: m.range(at: 1))) ?? 0) * 60 + (Int(ns.substring(with: m.range(at: 2))) ?? 0)
            var to = (Int(ns.substring(with: m.range(at: 3))) ?? 0) * 60 + (Int(ns.substring(with: m.range(at: 4))) ?? 0)
            if to == 0 { to = 24 * 60 }
            out.append((from, to))
        }
        return out
    }

    /// Open at this exact minute, in Amsterdam time (the Pro "open right now" filter).
    /// Distinct from `isOpenToday`, which only asks whether the day is one the farm
    /// opens at all. Mirrors web `isOpenNow`.
    static func isOpenNow(_ openingHours: String?, at date: Date = Date()) -> Bool {
        guard let raw = openingHours?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty
        else { return false }
        if raw == "24/7" { return true }

        let comps = amsterdamCalendar().dateComponents([.hour, .minute], from: date)
        let minutes = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        let dayMon = todayInAmsterdam(date)

        if closedDays(raw).contains(dayMon) { return false }

        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let s = segment.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { continue }
            if s.range(of: "\\boff\\b", options: [.regularExpression, .caseInsensitive]) != nil { continue }
            let dayPart = dayPartOf(s)
            if dayPart.isEmpty || !daysOf(dayPart).contains(dayMon) { continue }

            // A day named with no times at all is treated as open — some records say
            // "Mo-Sa" and nothing more, and refusing those would hide real farms.
            let windows = windowsOf(s)
            if windows.isEmpty { return true }
            for w in windows where minutes >= w.from && minutes < w.to { return true }
        }
        return false
    }

    /// Open on a given weekday, Mon-based (0 = Monday … 6 = Sunday) — the Pro "open
    /// Saturday/Sunday" filters. Mirrors web `isOpenOnDay`.
    static func isOpenOnDay(_ openingHours: String?, dayMon: Int) -> Bool {
        guard let raw = openingHours?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty
        else { return false }
        if raw == "24/7" { return true }
        if closedDays(raw).contains(dayMon) { return false }
        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let s = segment.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { continue }
            if s.range(of: "\\boff\\b", options: [.regularExpression, .caseInsensitive]) != nil { continue }
            let dayPart = dayPartOf(s)
            if !dayPart.isEmpty && daysOf(dayPart).contains(dayMon) { return true }
        }
        return false
    }

    /// Days a record explicitly closes — the `Su` in "…; Su off" — as Mon-indexed
    /// weekdays. `isOpenToday` subtracts these before it looks for an open day,
    /// because in OSM notation a later rule overrides an earlier one. Unknown
    /// tokens (e.g. `PH`) resolve to nothing and close no day. Mirrors web
    /// src/lib/opening-hours.ts closedDays().
    private static func closedDays(_ raw: String) -> Set<Int> {
        var out = Set<Int>()
        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let s = segment.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { continue }
            if s.range(of: "\\boff\\b", options: [.regularExpression, .caseInsensitive]) == nil { continue }
            let dayPart = s.replacingOccurrences(
                of: "\\boff\\b", with: "", options: [.regularExpression, .caseInsensitive]
            ).trimmingCharacters(in: .whitespaces)
            if dayPart.isEmpty { continue }
            out.formUnion(daysOf(dayPart))
        }
        return out
    }

    /// The Mon-indexed weekdays a day-part covers: "Mo-Fr" (range, wrap-aware),
    /// "Sa,Su" (comma list), "We" (single). Mirrors web daysOf().
    private static func daysOf(_ dayPart: String) -> Set<Int> {
        var out = Set<Int>()
        for group in dayPart.components(separatedBy: ",") {
            let g = group.trimmingCharacters(in: .whitespaces)
            if g.isEmpty { continue }
            if g.contains("-") {
                let parts = g.components(separatedBy: "-")
                guard parts.count == 2,
                      let ja = dayJS[dayKey(parts[0])], let jb = dayJS[dayKey(parts[1])],
                      let startMon = dayMon[ja], let endMon = dayMon[jb] else { continue }
                if startMon <= endMon {
                    for d in startMon...endMon { out.insert(d) }
                } else {
                    for d in startMon...6 { out.insert(d) }
                    for d in 0...endMon { out.insert(d) }
                }
            } else if let js = dayJS[dayKey(g)], let mon = dayMon[js] {
                out.insert(mon)
            }
        }
        return out
    }

    // MARK: - Farm vending machine (automaat)

    private static let automaatWords = [
        "automaat", "automat", "melktap", "eierautomaat", "kaasautomaat",
        "aardappelautomaat", "vending", "zelfbediening", "self-service", "selfservice",
    ]

    // MARK: - Open, shut, or unknown (R6)

    /// Three answers, not two.
    ///
    /// `isOpenOnDay` returns false both for a farm that is shut on Tuesday and
    /// for one whose hours nobody ever recorded. On a map that is tolerable. On
    /// a route it is not: 2,745 of 8,433 farms carry no `opening_hours` at all,
    /// and showing those as closed hides a third of the country from someone
    /// planning a Saturday. Calling them open is worse — that is how somebody
    /// drives forty minutes to a locked gate, and they do not blame OSM for it.
    ///
    /// Mirrors web `DayStatus` in src/lib/opening-hours.ts.
    enum DayStatus: String { case open, closed, unknown }

    /// Open, shut or unknown on a given weekday. Mirrors web `statusOnDay`.
    static func statusOnDay(_ openingHours: String?, dayMon: Int) -> DayStatus {
        let raw = openingHours?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty { return .unknown }
        if raw == "24/7" { return .open }
        if isOpenOnDay(raw, dayMon: dayMon) { return .open }

        // Nothing matched, which is two different situations. A string that
        // names weekdays and not this one is a farm that is shut. A string we
        // could not read a weekday out of — a bare "09:00-17:00", a note in
        // Dutch, something malformed — tells us nothing, and must not be dressed
        // up as a fact.
        return mentionsAWeekday(raw) ? .closed : .unknown
    }

    private static func mentionsAWeekday(_ raw: String) -> Bool {
        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let dayPart = dayPartOf(segment.trimmingCharacters(in: .whitespaces))
            if !dayPart.isEmpty && !daysOf(dayPart).isEmpty { return true }
        }
        return false
    }

    /// Every open window on a given weekday, as `[from, to)` minutes past
    /// midnight.
    ///
    /// Separate from `windowsOf`, which reads one segment and does not know what
    /// day it belongs to. A farm can carry different hours per day —
    /// `Mo-Fr 09:00-17:00; Sa 09:00-13:00` — and a Saturday trip must not be
    /// measured against the weekday row.
    ///
    /// An empty result does not mean shut. `Mo-Fr` with no times is a farm we
    /// know opens on Monday and whose hours nobody recorded; the caller has to
    /// tell those apart, which is why this returns windows and not a verdict.
    private static func windowsOnDay(_ raw: String, dayMon: Int) -> [(from: Int, to: Int)] {
        var out: [(from: Int, to: Int)] = []
        for segment in raw.components(separatedBy: CharacterSet(charactersIn: "\n;")) {
            let s = segment.trimmingCharacters(in: .whitespaces)
            if s.isEmpty { continue }
            if s.range(of: "\\boff\\b", options: [.regularExpression, .caseInsensitive]) != nil { continue }
            let dayPart = dayPartOf(s)
            if dayPart.isEmpty || !daysOf(dayPart).contains(dayMon) { continue }
            out.append(contentsOf: windowsOf(s))
        }
        return out
    }

    /// Open, shut or unknown on a given weekday, **between two times** (R6).
    ///
    /// `statusOnDay` answers "does this farm open at all on Saturday". That is
    /// the wrong question for a route: a shop open Saturday 09:00-13:00 is shut
    /// when you drive past at four, and a planner that lists it has sent someone
    /// to a locked gate just as surely as one with no hours at all.
    ///
    /// Times are minutes past midnight and the window is half-open, `[from, to)`.
    /// The same value twice asks about a single moment.
    ///
    /// The answer this exists to avoid: a farm whose day we know and whose hours
    /// we do not — `Mo-Fr`, no times — is **unknown**, never open. Open there is
    /// a guess dressed as a fact; closed would hide a farm that may be the one
    /// they wanted.
    ///
    /// Mirrors web `statusOnDayBetween`.
    static func statusOnDayBetween(
        _ openingHours: String?,
        dayMon: Int,
        fromMinutes: Int,
        toMinutes: Int
    ) -> DayStatus {
        let raw = openingHours?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if raw.isEmpty { return .unknown }
        if raw == "24/7" { return .open }

        // The day comes first: a farm shut on Saturday is shut at every hour of
        // it, and one whose hours are unreadable stays unreadable.
        let day = statusOnDay(raw, dayMon: dayMon)
        if day != .open { return day }

        let windows = windowsOnDay(raw, dayMon: dayMon)
        if windows.isEmpty { return .unknown }

        // A zero-length ask is a moment, not a span, so give it a minute of
        // width rather than answering "no overlap" for every farm on the map.
        let from = min(fromMinutes, toMinutes)
        let upper = max(fromMinutes, toMinutes)
        let end = upper == from ? from + 1 : upper

        for w in windows where w.from < end && from < w.to { return .open }
        return .closed
    }

    /// True when the text names a farm vending machine, or the hours say 24/7.
    static func looksLikeAutomaat(_ text: String?, openingHours: String? = nil) -> Bool {
        let t = (text ?? "").lowercased()
        if automaatWords.contains(where: { t.contains($0) }) { return true }
        let h = (openingHours ?? "").lowercased().replacingOccurrences(of: " ", with: "")
        return h.contains("24/7") || h.contains("24uur") || h.contains("00:00-24:00")
    }

    // MARK: - Pick your own (zelfpluk)

    private static let zelfplukWords = [
        "zelfpluk", "zelf plukken", "pluktuin", "plukboerderij", "zelfoogst",
        "pick your own", "u-pick", "self-pick", "cueillette",
    ]

    /// True when the text describes a pick-your-own farm.
    static func looksLikeZelfpluk(_ text: String?) -> Bool {
        let t = (text ?? "").lowercased()
        return zelfplukWords.contains(where: { t.contains($0) })
    }
}
