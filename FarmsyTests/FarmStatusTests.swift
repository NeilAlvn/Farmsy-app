import Foundation
import Testing
@testable import Farmsy

/// What visitors said about a farm, turned into one honest sentence.
///
/// Nothing here throws when it is wrong. A summary that leads with "people got
/// in" for a farm three people found shut sends somebody on a wasted drive, and
/// it looks exactly like a working feature while it does so. The web pins the
/// same rules in src/lib/statusReports.test.ts; these are the port's copy.
struct FarmStatusTests {

    static let now = Date(timeIntervalSince1970: 1_780_000_000)

    static func report(_ status: ReportStatus, daysAgo: Double) -> FarmReport {
        FarmReport(status: status, createdAt: now.addingTimeInterval(-daysAgo * 86_400))
    }

    static func summarise(_ reports: [FarmReport]) -> StatusSummary {
        FarmStatus.summarise(reports, now: now)
    }

    // ── The asymmetric warning ──────────────────────────────────────────────

    @Test("one closed against one open leads with the warning")
    func troubleLeadsWhenEven() {
        // The cost of the two errors is not symmetrical: a wasted drive is worse
        // than a second phone call.
        let s = Self.summarise([Self.report(.open, daysAgo: 1), Self.report(.closed, daysAgo: 2)])
        #expect(s.lead == .trouble)
    }

    @Test("one closed against four open is mixed, not a warning")
    func clearlyOutnumberedIsMixed() {
        // Probably somebody arriving after hours.
        let s = Self.summarise([
            Self.report(.open, daysAgo: 1), Self.report(.open, daysAgo: 2),
            Self.report(.open, daysAgo: 3), Self.report(.open, daysAgo: 4),
            Self.report(.closed, daysAgo: 5),
        ])
        #expect(s.lead == .mixed)
    }

    @Test("one closed against two open still warns — half is not outnumbered")
    func halfStillWarns() {
        let s = Self.summarise([
            Self.report(.open, daysAgo: 1), Self.report(.open, daysAgo: 2),
            Self.report(.closed, daysAgo: 3),
        ])
        #expect(s.lead == .trouble)
    }

    @Test("only open reports lead with open")
    func openLeads() {
        let s = Self.summarise([Self.report(.open, daysAgo: 1), Self.report(.open, daysAgo: 9)])
        #expect(s.lead == .open)
        #expect(s.open == 2)
        #expect(s.trouble == 0)
    }

    @Test("sold out counts as trouble, not as open")
    func soldOutIsTrouble() {
        let s = Self.summarise([Self.report(.soldOut, daysAgo: 1)])
        #expect(s.lead == .trouble)
        #expect(s.soldOut == 1)
        #expect(s.trouble == 1)
    }

    // ── No reports is not a verdict ─────────────────────────────────────────

    @Test("nothing said leads with nothing at all")
    func silenceSaysNothing() {
        let s = Self.summarise([])
        #expect(s.lead == .none)
        #expect(s.total == 0)
        #expect(s.daysAgo == nil)
    }

    @Test("a farm shut last March says nothing about this Saturday")
    func oldReportsAreNotShown() {
        let s = Self.summarise([Self.report(.closed, daysAgo: 60)])
        #expect(s.lead == .none)
        #expect(s.total == 0)
    }

    @Test("the cutoff is twenty-one days, inclusive of the edge")
    func cutoffBoundary() {
        #expect(Self.summarise([Self.report(.open, daysAgo: 20.9)]).total == 1)
        #expect(Self.summarise([Self.report(.open, daysAgo: 21.1)]).total == 0)
    }

    // ── A wrong clock is not a fact ─────────────────────────────────────────

    @Test("a report from the future is ignored")
    func futureReportsIgnored() {
        // Otherwise one wrong device clock pins a farm's status indefinitely.
        let s = Self.summarise([Self.report(.closed, daysAgo: -3)])
        #expect(s.total == 0)
        #expect(s.lead == .none)
    }

    // ── How long ago ────────────────────────────────────────────────────────

    @Test("daysAgo is the most recent report shown")
    func daysAgoIsTheNewest() {
        let s = Self.summarise([Self.report(.open, daysAgo: 9), Self.report(.open, daysAgo: 2)])
        #expect(s.daysAgo == 2)
    }

    // ── One vote per person per day ─────────────────────────────────────────

    @Test("today's own report is what the buttons show")
    func myReportToday() {
        let mine = [Self.report(.closed, daysAgo: 0)]
        #expect(FarmStatus.myReportToday(mine, now: Self.now) == .closed)
    }

    @Test("yesterday's own report does not light today's button")
    func yesterdayIsNotToday() {
        // A farm can be shut on Monday and open on Saturday, and both are true.
        let mine = [Self.report(.closed, daysAgo: 2)]
        #expect(FarmStatus.myReportToday(mine, now: Self.now) == nil)
    }

    @Test("the day is an Amsterdam day, not the device's")
    func amsterdamDay() {
        // 22:30 UTC on 1 June is already 2 June in Amsterdam (CEST, UTC+2).
        let lateEvening = ISO8601DateFormatter().date(from: "2026-06-01T22:30:00Z")!
        #expect(FarmStatus.amsterdamDay(lateEvening) == "2026-06-02")
    }

    // ── Timestamps as Postgres sends them ───────────────────────────────────

    @Test("timestamps parse with or without fractional seconds")
    func timestampParsing() {
        #expect(FarmStatus.parseTimestamp("2026-06-01T10:00:00Z") != nil)
        #expect(FarmStatus.parseTimestamp("2026-06-01T10:00:00.123456+00:00") != nil)
        #expect(FarmStatus.parseTimestamp("not a date") == nil)
    }
}
