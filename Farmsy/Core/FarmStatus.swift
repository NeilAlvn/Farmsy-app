import Foundation

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

    enum CodingKeys: String, CodingKey {
        case status
        case createdAt = "created_at"
    }

    init(status: ReportStatus, createdAt: Date) {
        self.status = status
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // An unknown status is a row from a newer server, not a crash.
        status = ReportStatus(rawValue: try c.decode(String.self, forKey: .status)) ?? .open
        let raw = try c.decode(String.self, forKey: .createdAt)
        createdAt = FarmStatus.parseTimestamp(raw) ?? .distantPast
    }
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
    static func report(osmId: String, status: ReportStatus, token: String) async -> Bool {
        var request = URLRequest(url: url(osmId))
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["status": status.rawValue])
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
}
