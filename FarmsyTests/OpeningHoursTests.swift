import Foundation
import Testing
@testable import Farmsy

/// R6: is this farm open when I drive past it, and the day-name gap found while
/// building it.
///
/// Two things are covered here and the second is the more serious.
///
/// **The ticket.** `statusOnDay` answers "does this farm open at all on
/// Saturday". A route needs "is it open at four", and a shop open 09:00-13:00 is
/// shut then. Three answers, never two: a day we know with hours we do not —
/// `Mo-Fr`, no times — is *unknown*, never open. Open there is a guess dressed
/// as a fact; closed hides a farm that may be the one they wanted.
///
/// **The gap.** Half the hours in the database are written in Dutch —
/// 2,762 of the 5,688 farms that have any. `dayJS` carried only OSM's English
/// abbreviations, so every one of those parsed as "never open", on every day.
/// That silently emptied the free open-today filter and the paid open-right-now
/// one of half their data. The web fixed this; the apps never got the fix.
/// The language cases below are the regression test for it.
struct OpeningHoursTests {

    static let mon = 0
    static let sat = 5
    static func at(_ h: Int, _ m: Int = 0) -> Int { h * 60 + m }

    // MARK: - The question a route asks

    @Test("open that day, and open when you pass")
    func openWhenPassing() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-17:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .open)
    }

    @Test("open that day, shut when you pass")
    func shutWhenPassing() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(16), toMinutes: Self.at(17)) == .closed)
    }

    @Test("a window that only clips the edge of the drive still counts")
    func clippingEdge() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(12, 30), toMinutes: Self.at(13, 30)) == .open)
    }

    @Test("touching endpoints do not overlap — the window is half-open")
    func halfOpenWindow() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(13), toMinutes: Self.at(14)) == .closed)
    }

    @Test("a single moment is a valid question")
    func singleMoment() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(11), toMinutes: Self.at(11)) == .open)
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(13), toMinutes: Self.at(13)) == .closed)
    }

    @Test("two windows in one day — the lunch break is a real closure")
    func lunchBreak() {
        let hours = "Sa 09:00-12:00,13:00-17:00"
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.sat,
                                               fromMinutes: Self.at(12, 10), toMinutes: Self.at(12, 50)) == .closed)
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.sat,
                                               fromMinutes: Self.at(13, 30), toMinutes: Self.at(14)) == .open)
    }

    @Test("the Saturday row is read, not the weekday row above it")
    func perDayWindows() {
        let hours = "Mo-Fr 09:00-17:00; Sa 13:00-16:00"
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.sat,
                                               fromMinutes: Self.at(9), toMinutes: Self.at(10)) == .closed)
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.sat,
                                               fromMinutes: Self.at(14), toMinutes: Self.at(15)) == .open)
    }

    // MARK: - The refusals to guess

    @Test("no hours recorded stays unknown, at every time of day")
    func noHoursUnknown() {
        #expect(FarmFilters.statusOnDayBetween(nil, dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .unknown)
        #expect(FarmFilters.statusOnDayBetween("", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .unknown)
    }

    @Test("a day we know with hours we do not is unknown, never open")
    func dayKnownHoursNot() {
        #expect(FarmFilters.statusOnDayBetween("Mo-Fr", dayMon: Self.mon,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .unknown)
        #expect(FarmFilters.statusOnDayBetween("Sa", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .unknown)
    }

    @Test("a farm shut on that day is shut at every hour of it")
    func shutAllDay() {
        #expect(FarmFilters.statusOnDayBetween("Mo-Fr 09:00-17:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .closed)
    }

    @Test("an explicit off still beats the rule above it")
    func explicitOff() {
        #expect(FarmFilters.statusOnDayBetween("Mo-Su 09:00-17:00; Sa off", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(12)) == .closed)
    }

    @Test("statusOnDay tells shut apart from unreadable")
    func dayLevelThreeStates() {
        #expect(FarmFilters.statusOnDay(nil, dayMon: Self.sat) == .unknown)
        #expect(FarmFilters.statusOnDay("09:00-17:00", dayMon: Self.sat) == .unknown)  // no weekday named
        #expect(FarmFilters.statusOnDay("Mo-Fr 09:00-17:00", dayMon: Self.sat) == .closed)
        #expect(FarmFilters.statusOnDay("Sa 09:00-17:00", dayMon: Self.sat) == .open)
    }

    // MARK: - The shapes the data actually carries

    @Test("24/7 is open whenever you ask")
    func alwaysOpen() {
        #expect(FarmFilters.statusOnDayBetween("24/7", dayMon: Self.sat,
                                               fromMinutes: Self.at(3), toMinutes: Self.at(4)) == .open)
    }

    @Test("a closing time of 00:00 means midnight at the end of the day")
    func midnightClose() {
        #expect(FarmFilters.statusOnDayBetween("Sa 09:00-00:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(21), toMinutes: Self.at(22)) == .open)
    }

    // MARK: - The day-name gap (the regression this file exists for)

    @Test("Dutch day names parse — half the hours in the database are written this way",
          arguments: [
            ("maandag: 09:00-17:00", 0),
            ("ma 09:00-17:00", 0),
            ("dinsdag 09:00-17:00", 1),
            ("zaterdag: 09:00-13:00", 5),
            ("za 09:00-13:00", 5),
            ("zondag 10:00-16:00", 6),
          ])
    func dutchDayNames(hours: String, day: Int) {
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: day,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .open)
    }

    @Test("Dutch ranges parse too")
    func dutchRanges() {
        #expect(FarmFilters.statusOnDayBetween("za-zo 10:00-16:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(11), toMinutes: Self.at(12)) == .open)
        #expect(FarmFilters.statusOnDayBetween("maandag-vrijdag 09:00-17:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .closed)
    }

    // The day-name fix landed but the TIME-range dash did not: the imports write both
    // their day ranges AND their times with an en dash (`09:00–17:00`), and the window
    // regex only accepted the ASCII hyphen. So the day parsed, the hours did not, and
    // the segment fell through to "a day named with no times = open". Every test above
    // uses a hyphen, which is exactly how this hid.
    @Test("en-dash and em-dash TIME ranges parse — the imports write 09:00–17:00, not 09:00-17:00",
          arguments: ["maandag: 09:00\u{2013}17:00", "maandag: 09:00\u{2014}17:00"])
    func endashTimeRanges(hours: String) {
        // Inside the window on Monday…
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.mon,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .open)
        // …and shut outside it. Before the fix the window was empty, which a route
        // reads as `unknown` and the paid `isOpenNow` read as open-all-day.
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.mon,
                                               fromMinutes: Self.at(3), toMinutes: Self.at(4)) == .closed)
    }

    @Test("isOpenNow with an en-dash time range is a real window, not open all day — the paid-filter inversion")
    func isOpenNowEndashNotAllDay() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam")!
        var c = DateComponents(); c.year = 2026; c.month = 6; c.day = 1  // any date — Mo-Su covers every weekday
        c.hour = 3;  let at3am  = cal.date(from: c)!
        c.hour = 12; let atNoon = cal.date(from: c)!
        let hours = "Mo-Su 09:00\u{2013}17:00"   // en dash between the two times
        // Before the fix this answered `true` at 03:00 — a member sent to a gate shut
        // six hours ago, which is the one thing this filter exists to prevent.
        #expect(FarmFilters.isOpenNow(hours, at: at3am)  == false)
        #expect(FarmFilters.isOpenNow(hours, at: atNoon) == true)
    }

    @Test("French and German parse — Belgium is bilingual and the German import shares the shape")
    func otherLanguages() {
        #expect(FarmFilters.statusOnDayBetween("samedi 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .open)
        #expect(FarmFilters.statusOnDayBetween("samstag 09:00-13:00", dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .open)
    }

    // MARK: - "Closed" written in a language OSM does not use

    @Test("a day the farm calls closed is closed, in every word our data uses",
          arguments: [
            "Mo-Su 09:00-17:00; Su off",
            "Mo-Su 09:00-17:00; Su gesloten",
            "Mo-Su 09:00-17:00; zondag gesloten",
            "Mo-Su 09:00-17:00; Su closed",
          ])
    func closedInAnyLanguage(hours: String) {
        // Before the off pattern grew, only the first of these was read as shut.
        // The other three still named a weekday and nothing marked them closed,
        // so the app answered OPEN for a farm whose own hours say otherwise —
        // the locked gate, actively caused rather than merely missed.
        #expect(FarmFilters.isOpenOnDay(hours, dayMon: 6) == false)
        #expect(FarmFilters.statusOnDay(hours, dayMon: 6) == .closed)
    }

    @Test("closing one day does not close the rest of the week")
    func offIsPerDay() {
        let hours = "Mo-Su 09:00-17:00; Su gesloten"
        #expect(FarmFilters.isOpenOnDay(hours, dayMon: Self.sat))
        #expect(FarmFilters.statusOnDayBetween(hours, dayMon: Self.sat,
                                               fromMinutes: Self.at(10), toMinutes: Self.at(11)) == .open)
    }

    // MARK: - Ranges written with a dash that is not a hyphen

    @Test("an en dash and an em dash are ranges too",
          arguments: ["ma-vr", "ma\u{2013}vr", "ma\u{2014}vr", "Mo\u{2013}Fr", "Mo-Fr"])
    func dashesInDayRanges(range: String) {
        // The Dutch imports write both their day ranges and their times with an
        // en dash. Splitting on the ASCII hyphen alone read `ma–vr` as one
        // unknown token, so the farm was open on no day at all.
        #expect(FarmFilters.isOpenOnDay("\(range) 09:00-17:00", dayMon: Self.mon))
        #expect(FarmFilters.isOpenOnDay("\(range) 09:00-17:00", dayMon: Self.sat) == false)
    }

    @Test("isOpenToday is exactly isOpenOnDay for today")
    func openTodayDelegates() {
        // It carried its own copy of the day matching, so every fix had to be
        // made twice — and the second place is how a filter and a planner come
        // to disagree about the same farm. This is the invariant that keeps the
        // two from drifting again.
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam")!
        let js = cal.component(.weekday, from: Date()) - 1
        let todayMon = [1: 0, 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 0: 6][js]!

        for hours in ["Mo-Fr 09:00-17:00", "za 09:00-13:00", "ma\u{2013}vr 09:00-17:00",
                      "24/7", "Mo-Su 09:00-17:00; Su gesloten", "arbitrary text", ""] {
            #expect(FarmFilters.isOpenToday(hours) == FarmFilters.isOpenOnDay(hours, dayMon: todayMon))
        }
    }

    @Test("the existing filters see the Dutch farms now too")
    func existingFiltersFixed() {
        // These returned false for every Dutch-format farm before the day table
        // grew — which is the whole bug, and it is invisible because it removes
        // farms rather than adding wrong ones.
        #expect(FarmFilters.isOpenOnDay("zaterdag: 09:00-13:00", dayMon: Self.sat))
        #expect(FarmFilters.isOpenOnDay("ma-vr 09:00-17:00", dayMon: Self.mon))
        // And OSM's own format still works.
        #expect(FarmFilters.isOpenOnDay("Mo-Fr 09:00-17:00", dayMon: Self.mon))
    }
}

/// R5b · the row says what the farm sells.
///
/// The bug this closes is invisible: the produce line fell back to the town name, so a
/// planner whose whole pitch is "cheese on your way" listed farms and named towns, and
/// nobody reading "Utrecht" under a farm name thinks anything is missing.
struct CorridorSellsTests {

    @Test("a produce list becomes a row of things, not a sentence")
    func fourAtMost() {
        #expect(RouteCorridorSells.sells("cheese, milk") == "cheese · milk")
        #expect(RouteCorridorSells.sells("cheese,milk,eggs") == "cheese · milk · eggs")
    }

    @Test("a long list is cut rather than truncated mid-word")
    func cut() {
        // The row is one line. Five things that get clipped by the layout say less
        // than four that fit.
        #expect(RouteCorridorSells.sells("a, b, c, d, e, f") == "a · b · c · d")
    }

    @Test("nothing to say returns nil, so the caller can fall back to the town")
    func emptyIsNil() {
        #expect(RouteCorridorSells.sells(nil) == nil)
        #expect(RouteCorridorSells.sells("") == nil)
        #expect(RouteCorridorSells.sells("   ") == nil)
        #expect(RouteCorridorSells.sells(",,, ,") == nil)
    }

    @Test("spacing in the stored text does not reach the screen")
    func trimmed() {
        #expect(RouteCorridorSells.sells("  cheese ,   milk  ") == "cheese · milk")
    }
}
