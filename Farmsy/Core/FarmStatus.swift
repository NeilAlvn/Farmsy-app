import Foundation
import CoreLocation
import Observation

// "Was it open when you got there?" — one tap, from the farm card.
//
// WHY THIS CARRIES MORE THAN IT LOOKS
// Google Places was dropped, which took away permanently-closed detection.
// Track V was paused, which took away ringing every farm in a province. This is
// the only thing in phase one that says a listing has gone stale, and it costs
// a visitor one tap on a screen they were already looking at.
//
// WHAT IT IS NOT
// Not the farmer speaking. There are no farmers on the platform yet, so every
// report here is another visitor's. The copy has to keep saying so: "sold out"
// from the shop itself and "somebody found it sold out" are different claims,
// and only one of them is true today.
//
// Port of src/lib/statusReports.ts. The two rules below are the whole file, and
// the web has the same test pinning them.

/// What a visitor found. Deliberately no "I don't know": somebody unsure should
/// say nothing, and the absence of reports is already how we say we do not know.
enum ReportStatus: String, Codable, CaseIterable, Sendable {
    case open, closed, soldOut = "sold_out"
}

struct FarmReport: Decodable, Sendable {
    let status: ReportStatus
    let createdAt: Date
    /// Shopping-list item ids the visitor found in stock. Empty = did not say,
    /// never "had nothing".
    let products: [String]

    enum CodingKeys: String, CodingKey {
        case status, products
        case createdAt = "created_at"
    }

    init(status: ReportStatus, createdAt: Date, products: [String] = []) {
        self.status = status
        self.createdAt = createdAt
        self.products = products
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // An unknown status is a row from a newer server, not a crash.
        status = ReportStatus(rawValue: try c.decode(String.self, forKey: .status)) ?? .open
        let raw = try c.decode(String.self, forKey: .createdAt)
        createdAt = FarmStatus.parseTimestamp(raw) ?? .distantPast
        products = (try? c.decodeIfPresent([String].self, forKey: .products)) ?? []
    }
}

/// One row of GET /api/status/recent: a report with the farm it is about.
struct RecentReport: Decodable, Identifiable, Sendable {
    let farmOsmId: String
    let report: FarmReport
    var id: String { "\(farmOsmId)|\(report.createdAt.timeIntervalSince1970)" }

    enum CodingKeys: String, CodingKey { case farmOsmId = "farm_osm_id" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        farmOsmId = try c.decode(String.self, forKey: .farmOsmId)
        report = try FarmReport(from: decoder)
    }
}

/// The Plus half of a report: not just "3 people this month" but "18 minutes
/// ago, and 3 people today agree".
struct Freshness: Equatable {
    let status: ReportStatus
    let minutesAgo: Int
    /// Reports with the same status in the last 24 hours, the newest included.
    let confirmations: Int
    /// Item ids seen in those same reports, newest first, unique.
    let products: [String]
}

/// What the panel should lead with.
enum StatusLead {
    /// Say nothing at all. No reports is not a verdict.
    case none
    /// Recent visitors got in.
    case open
    /// Recent visitors found it shut or sold out. Worth a warning even when
    /// others got in, because the cost of the two errors is not symmetrical: a
    /// wasted drive is worse than a second phone call.
    case trouble
    /// Enough of both that neither leads.
    case mixed
}

struct StatusSummary: Equatable {
    var open = 0
    var closed = 0
    var soldOut = 0
    var total = 0
    /// Days since the most recent report shown, or nil when there are none.
    var daysAgo: Int?
    var lead: StatusLead = .none

    var trouble: Int { closed + soldOut }

    static func == (a: StatusSummary, b: StatusSummary) -> Bool {
        a.open == b.open && a.closed == b.closed && a.soldOut == b.soldOut
            && a.total == b.total && a.daysAgo == b.daysAgo && a.lead == b.lead
    }
}

extension StatusLead: Equatable {}

enum FarmStatus {

    /// Reports older than this are kept but not shown. A farm that was shut last
    /// March tells you nothing about this Saturday.
    static let recentDays = 21

    /// Summarise the reports for one farm. `now` is injectable so a test does
    /// not depend on the clock.
    ///
    /// Counts and recency, never a percentage: "50% found it open" is a lie with
    /// a decimal point in it, because at this volume there is no sample — there
    /// are two people, one of whom went on a Tuesday.
    static func summarise(_ reports: [FarmReport], now: Date = Date()) -> StatusSummary {
        let cutoff = now.addingTimeInterval(-Double(recentDays) * 86_400)
        let tomorrow = now.addingTimeInterval(86_400)

        var s = StatusSummary()
        var newest: Date?

        for r in reports {
            guard r.createdAt >= cutoff else { continue }
            // A report dated in the future is a clock problem, not a fact.
            // Counting it would let one wrong device clock pin a farm's status.
            guard r.createdAt <= tomorrow else { continue }

            switch r.status {
            case .open:    s.open += 1
            case .closed:  s.closed += 1
            case .soldOut: s.soldOut += 1
            }
            if newest == nil || r.createdAt > newest! { newest = r.createdAt }
        }

        s.total = s.open + s.closed + s.soldOut
        if let newest {
            s.daysAgo = Int(now.timeIntervalSince(newest) / 86_400)
        }

        if s.total > 0 {
            // Trouble leads unless it is clearly outnumbered. One "closed"
            // against four "open" is probably somebody arriving after hours; one
            // against one is not something to wave away.
            if s.trouble > 0 && s.trouble * 2 >= s.open { s.lead = .trouble }
            else if s.trouble > 0 { s.lead = .mixed }
            else { s.lead = .open }
        }
        return s
    }

    /// The newest report within the last 24 hours, with how many agreed and what
    /// they found. Nil when the freshest word is older than a day: "confirmed 26
    /// hours ago" is not confirmation, it is history, and the day-level summary
    /// already says it.
    static func freshness(_ reports: [FarmReport], now: Date = Date()) -> Freshness? {
        let dayAgo = now.addingTimeInterval(-86_400)
        let recent = reports.filter { $0.createdAt >= dayAgo && $0.createdAt <= now.addingTimeInterval(86_400) }
            .sorted { $0.createdAt > $1.createdAt }
        guard let newest = recent.first else { return nil }
        let agreeing = recent.filter { $0.status == newest.status }
        var seen = Set<String>()
        let products = agreeing.flatMap(\.products).filter { seen.insert($0).inserted }
        return Freshness(status: newest.status,
                         minutesAgo: max(0, Int(now.timeIntervalSince(newest.createdAt) / 60)),
                         confirmations: agreeing.count,
                         products: products)
    }

    /// Whether this person already reported today, and what they said.
    ///
    /// The table allows one row per person per farm per Amsterdam day, so the
    /// buttons show their own state rather than offering to vote again.
    static func myReportToday(_ mine: [FarmReport], now: Date = Date()) -> ReportStatus? {
        let today = amsterdamDay(now)
        for r in mine where amsterdamDay(r.createdAt) == today { return r.status }
        return nil
    }

    /// `YYYY-MM-DD` in Amsterdam, matching the generated column in migration 065.
    /// The farms are in NL and BE; a visitor's own timezone is not the question
    /// being answered.
    static func amsterdamDay(_ at: Date) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .gmt
        let c = cal.dateComponents([.year, .month, .day], from: at)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// Postgres timestamptz varies in fractional-second precision; normalise
    /// before ISO-8601 parsing. Same shape as SessionStore.parsePostgresDate.
    static func parseTimestamp(_ raw: String) -> Date? {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        let noFraction = raw.replacingOccurrences(of: #"\.\d+"#, with: "", options: .regularExpression)
        return iso.date(from: noFraction) ?? iso.date(from: noFraction + "Z")
    }
}

// MARK: - API

/// GET / POST / DELETE /api/farm/{osmId}/status.
///
/// The website calls the server actions directly; the app cannot, so the web
/// wraps them in this route. One place decides who may write, and there is no
/// second copy of the one-per-day rule to drift.
enum FarmStatusAPI {

    struct Loaded: Decodable, Sendable {
        let reports: [FarmReport]
        /// This caller's own reports, so the buttons can show their own state.
        let mine: [FarmReport]
    }

    private static func url(_ osmId: String) -> URL {
        Backend.webAPI
            .appending(path: "farm")
            .appending(path: osmId)
            .appending(path: "status")
    }

    /// Reading is public: no token, no account. Failure returns nothing rather
    /// than throwing — on this panel "we could not look" and "nobody has said"
    /// look the same and read the same.
    static func load(osmId: String, token: String?) async -> Loaded? {
        var request = URLRequest(url: url(osmId))
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        guard let (data, resp) = try? await URLSession.shared.data(for: request),
              (resp as? HTTPURLResponse)?.statusCode == 200
        else { return nil }
        return try? JSONDecoder().decode(Loaded.self, from: data)
    }

    /// Report what you found. Sending the same status twice is a correction, not
    /// a second vote — the caller clears instead.
    static func report(osmId: String, status: ReportStatus, products: [String] = [], token: String) async -> Bool {
        var request = URLRequest(url: url(osmId))
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["status": status.rawValue, "products": products])
        guard let (_, resp) = try? await URLSession.shared.data(for: request) else { return false }
        return (resp as? HTTPURLResponse)?.statusCode == 200
    }

    /// Take back today's report.
    static func clear(osmId: String, token: String) async -> Bool {
        var request = URLRequest(url: url(osmId))
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        guard let (_, resp) = try? await URLSession.shared.data(for: request) else { return false }
        return (resp as? HTTPURLResponse)?.statusCode == 200
    }

    /// Every report from the last `days` days across all farms, newest first.
    /// No geography on the wire: the caller holds every pin and filters itself.
    static func recent(days: Int = 7) async -> [RecentReport] {
        var url = Backend.webAPI.appending(path: "status").appending(path: "recent")
        url.append(queryItems: [URLQueryItem(name: "days", value: String(days))])
        guard let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let decoded = try? JSONDecoder().decode(RecentPayload.self, from: data)
        else { return [] }
        return decoded.reports
    }

    private struct RecentPayload: Decodable { let reports: [RecentReport] }
}

/// The recent-reports feed, fetched once a minute at most and shared by Home,
/// Community and the farm card, so three screens do not ask three times.
@MainActor
@Observable
final class RecentReports {
    static let shared = RecentReports()
    private(set) var reports: [RecentReport] = []
    private(set) var loadedAt: Date?
    private init() {}

    func refresh(force: Bool = false) async {
        if !force, let loadedAt, Date().timeIntervalSince(loadedAt) < 60 { return }
        reports = await FarmStatusAPI.recent()
        loadedAt = Date()
    }

    /// Farms a visitor reported open today (Amsterdam day). What the map's
    /// "Confirmed open today" chip and the vivid pin ring are built on.
    var confirmedOpenToday: Set<String> {
        let today = FarmStatus.amsterdamDay(Date())
        return Set(reports.filter { $0.report.status == .open && FarmStatus.amsterdamDay($0.report.createdAt) == today }
            .map(\.farmOsmId))
    }

    /// Reports about farms within `radiusKm` of `origin`, using the pins the
    /// store already holds. No origin means everywhere.
    func near(_ origin: CLLocation?, radiusKm: Double, pins: [String: FarmPin]) -> [RecentReport] {
        guard let origin else { return reports }
        return reports.filter { r in
            guard let pin = pins[r.farmOsmId], let d = pin.distance(from: origin) else { return false }
            return d / 1000 <= radiusKm
        }
    }
}
