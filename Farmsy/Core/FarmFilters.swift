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

        // Calendar weekday: 1 = Sunday … 7 = Saturday → JS getDay() 0 = Sunday.
        let jsToday = Calendar.current.component(.weekday, from: Date()) - 1
        guard let todayMon = dayMon[jsToday] else { return false }

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
