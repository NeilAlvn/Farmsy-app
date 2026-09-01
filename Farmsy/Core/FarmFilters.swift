import Foundation

// Quick-filter predicates, ported verbatim from the web (src/lib/opening-hours.ts
// and src/app/map/mapSearch.ts) so the app filters the same farms the site does.
enum FarmFilters {

    // MARK: - Open today

    private static let dayMon: [Int: Int] = [1: 0, 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 0: 6]
    private static let dayJS: [String: Int] = ["Mo": 1, "Tu": 2, "We": 3, "Th": 4, "Fr": 5, "Sa": 6, "Su": 0]

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
                          let ja = dayJS[parts[0]], let jb = dayJS[parts[1]],
                          let startMon = dayMon[ja], let endMon = dayMon[jb] else { continue }
                    if startMon <= endMon {
                        if todayMon >= startMon && todayMon <= endMon { return true }
                    } else {
                        if todayMon >= startMon || todayMon <= endMon { return true }
                    }
                } else if let js = dayJS[g], dayMon[js] == todayMon {
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
                      let ja = dayJS[parts[0]], let jb = dayJS[parts[1]],
                      let startMon = dayMon[ja], let endMon = dayMon[jb] else { continue }
                if startMon <= endMon {
                    for d in startMon...endMon { out.insert(d) }
                } else {
                    for d in startMon...6 { out.insert(d) }
                    for d in 0...endMon { out.insert(d) }
                }
            } else if let js = dayJS[g], let mon = dayMon[js] {
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
