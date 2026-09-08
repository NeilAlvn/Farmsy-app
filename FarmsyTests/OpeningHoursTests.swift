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
