import Foundation
import Testing
@testable import Farmsy

/// R7: a saved trip remembers when it was for.
///
/// The same rules live on the web in `tripEndpoints.ts` and again in migration
/// 059 as a CHECK constraint. Three implementations of one truth, the same
/// arrangement as R1 — if a rule changes here it changes there, in the same
/// commit.
///
/// What this is for: "drive this again" cannot say what changed without knowing
/// what the trip was compared against. A trip that remembers its ends but not
/// its day can be redrawn and cannot be re-answered — every farm on it would
/// report today, and the comparison would be between a saved route and nothing.
///
/// The bug guarded here is quieter than a missing day. It is a day that is not
/// a day. `2026-02-31` matches `yyyy-mm-dd` and is not a date; a calendar rolls
/// it forward to March, and the trip then compares opening hours against the
/// wrong weekday and reports it with full confidence.
struct TripWhenTests {

    // MARK: - A date that is really a date

    @Test("a real date survives")
    func realDates() {
        #expect(TripEndpoints.validDate("2026-09-12") == "2026-09-12")
        #expect(TripEndpoints.validDate("2024-02-29") == "2024-02-29", "2024 is a leap year")
        #expect(TripEndpoints.validDate("2026-12-31") == "2026-12-31")
    }

    @Test("a date that matches the shape but is not a day is refused")
    func impossibleDates() {
        // Each of these passes a regex and fails a calendar.
        #expect(TripEndpoints.validDate("2026-02-31") == nil)
        #expect(TripEndpoints.validDate("2026-02-29") == nil, "2026 is not a leap year")
        #expect(TripEndpoints.validDate("2026-13-01") == nil)
        #expect(TripEndpoints.validDate("2026-00-10") == nil)
        #expect(TripEndpoints.validDate("2026-04-31") == nil)
    }

    @Test("anything that is not yyyy-mm-dd is refused")
    func malformedDates() {
        for bad in ["", "  ", "2026-9-12", "12-09-2026", "2026/09/12", "2026-09-12T00:00:00Z",
                    "yyyy-mm-dd", "2026-09-1x", "20260912"] {
            #expect(TripEndpoints.validDate(bad) == nil, "\(bad)")
        }
        #expect(TripEndpoints.validDate(nil) == nil)
    }

    // MARK: - Minutes past midnight

    @Test("a departure inside the day survives")
    func realDepartures() {
        #expect(TripEndpoints.validDepartMinutes(0) == 0, "midnight is a time")
        #expect(TripEndpoints.validDepartMinutes(9 * 60 + 30) == 570)
        #expect(TripEndpoints.validDepartMinutes(1439) == 1439, "23:59 is the last minute")
    }

    @Test("a departure outside the day is refused, matching the 059 CHECK")
    func impossibleDepartures() {
        #expect(TripEndpoints.validDepartMinutes(-1) == nil)
        #expect(TripEndpoints.validDepartMinutes(1440) == nil, "24:00 is tomorrow")
        #expect(TripEndpoints.validDepartMinutes(99999) == nil)
        #expect(TripEndpoints.validDepartMinutes(nil) == nil)
    }

    // MARK: - The round trip

    @Test("a day survives being saved and reopened")
    func roundTrip() {
        let planned = TripEndpoints(
            origin: TripPlace.make(lat: 52.0907, lng: 5.1214, label: "Utrecht"),
            destination: TripPlace.make(lat: 53.2194, lng: 6.5665, label: "Groningen"),
            radiusKm: 8,
            date: "2026-09-12",
            departMinutes: 570
        )
        let reopened = TripEndpointRow(planned).endpoints
        #expect(reopened.date == "2026-09-12")
        #expect(reopened.departMinutes == 570)
        #expect(reopened == planned)
    }

    @Test("a trip from before R7 reopens with no day, and invents none")
    func preR7() {
        // Every trip saved before 059 has NULL in both columns. Decoding one
        // must produce a usable plan, not an error and not today's date frozen
        // into a trip that never had one.
        let row = TripEndpointRow(TripEndpoints.none)
        #expect(row.trip_date == nil)
        #expect(row.depart_minutes == nil)
        #expect(row.endpoints.date == nil)
        #expect(row.endpoints.departMinutes == nil)
    }

    @Test("a bad day never reaches the database")
    func badValuesAreDroppedOnTheWayOut() {
        // The CHECK in 059 would reject the row and lose the whole save. Drop
        // the bad field here instead: a trip with no day still saves.
        var plan = TripEndpoints.none
        plan.date = "2026-02-31"
        plan.departMinutes = 1440

        let row = TripEndpointRow(plan)
        #expect(row.trip_date == nil)
        #expect(row.depart_minutes == nil)
    }

    @Test("every column the row writes is a column the query reads back")
    func columnsAgree() {
        // The failure this prevents: a column added to the row and forgotten in
        // the select, so the value is written and never seen again.
        for column in ["trip_date", "depart_minutes", "radius_km",
                       "origin_lat", "origin_lng", "origin_label",
                       "destination_lat", "destination_lng", "destination_label"] {
            #expect(TripEndpointRow.columns.contains(column), "\(column) is selected")
        }
    }
}

/// The calendar helpers the picker is built on. Separated from the storage
/// rules above because these are the ones a time zone can break quietly.
struct TripDayTests {

    let cal = TripEndpoints.calendar

    @Test("a date survives being turned into a Date and back")
    func roundTripThroughDate() {
        for iso in ["2026-09-12", "2024-02-29", "2026-01-01", "2026-12-31"] {
            let back = TripEndpoints.day(from: iso).map(TripEndpoints.dayString)
            #expect(back == iso, "\(iso)")
        }
    }

    @Test("a stored day is anchored at noon, not midnight")
    func noonNotMidnight() {
        // Midnight is one hour from being the previous day, and a daylight
        // saving boundary moves things by exactly that twice a year. The Dutch
        // clocks go forward on the last Sunday of March.
        let dst = TripEndpoints.day(from: "2026-03-29")
        #expect(dst != nil)
        #expect(TripEndpoints.dayString(dst!) == "2026-03-29", "the day did not slide")

        let autumn = TripEndpoints.day(from: "2026-10-25")
        #expect(autumn != nil)
        #expect(TripEndpoints.dayString(autumn!) == "2026-10-25")
    }

    @Test("an unusable day makes no Date at all")
    func badDaysMakeNoDate() {
        #expect(TripEndpoints.day(from: "2026-02-31") == nil)
        #expect(TripEndpoints.day(from: "not a date") == nil)
        #expect(TripEndpoints.day(from: nil) == nil)
    }

    @Test("the default day is always a Saturday")
    func defaultIsSaturday() {
        // Every day of one week, so the modulo is exercised in both directions
        // rather than only on whatever day the suite happens to run.
        let start = cal.date(from: DateComponents(year: 2026, month: 9, day: 7, hour: 12))!
        for offset in 0..<7 {
            let from = cal.date(byAdding: .day, value: offset, to: start)!
            let saturday = TripEndpoints.nextSaturday(from: from)

            let asDate = TripEndpoints.day(from: saturday)
            #expect(asDate != nil, "\(saturday)")
            #expect(cal.component(.weekday, from: asDate!) == 7, "\(saturday) is a Saturday")
        }
    }

    @Test("today counts when today is Saturday")
    func saturdayIsItsOwnDefault() {
        // Otherwise somebody opening the planner on a Saturday morning is
        // offered next weekend for the trip they are about to drive.
        let saturday = cal.date(from: DateComponents(year: 2026, month: 9, day: 12, hour: 12))!
        #expect(cal.component(.weekday, from: saturday) == 7, "the fixture really is a Saturday")
        #expect(TripEndpoints.nextSaturday(from: saturday) == "2026-09-12")
    }

    @Test("the default day is never in the past")
    func defaultIsNeverBehind() {
        let today = TripEndpoints.dayString(Date())
        #expect(TripEndpoints.nextSaturday() >= today, "yyyy-mm-dd sorts as it reads")
    }
}
