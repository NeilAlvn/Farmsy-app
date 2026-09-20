import Foundation
import Observation

// Badges and the leaderboard: what a person gave the map, as the server counts
// it (migration 069). The app only reads; award_badges runs server-side after
// every report and post, so nothing here can be earned by tapping.

/// The eight badges, in display order, with their rule in words.
enum BadgeKind: String, CaseIterable, Identifiable {
    case firstReport = "first_report"
    case earlyBird = "early_bird"
    case confirmer
    case explorer
    case shelfScout = "shelf_scout"
    case photographer
    case regular
    case collector

    var id: String { rawValue }

    var title: String {
        switch self {
        case .firstReport: String(localized: "First report")
        case .earlyBird: String(localized: "Early bird")
        case .confirmer: String(localized: "Confirmer")
        case .explorer: String(localized: "Explorer")
        case .shelfScout: String(localized: "Shelf scout")
        case .photographer: String(localized: "Photographer")
        case .regular: String(localized: "Regular")
        case .collector: String(localized: "Collector")
        }
    }

    var rule: String {
        switch self {
        case .firstReport: String(localized: "Tell the app whether a farm was open, once.")
        case .earlyBird: String(localized: "Be the first to report a farm that day, five times.")
        case .confirmer: String(localized: "Confirm what someone else reported that day, ten times.")
        case .explorer: String(localized: "Report at ten different farms.")
        case .shelfScout: String(localized: "Say what was on the shelf in 25 reports.")
        case .photographer: String(localized: "Post a photo from a farm.")
        case .regular: String(localized: "Report in four different weeks.")
        case .collector: String(localized: "Save five farms.")
        }
    }

    var icon: String {
        switch self {
        case .firstReport: "flag.fill"
        case .earlyBird: "sunrise.fill"
        case .confirmer: "checkmark.seal.fill"
        case .explorer: "map.fill"
        case .shelfScout: "basket.fill"
        case .photographer: "camera.fill"
        case .regular: "calendar"
        case .collector: "heart.fill"
        }
    }
}

struct ContributionStats: Decodable, Sendable {
    let reports: Int
    let farms: Int
    let confirmations: Int
    let earlyBirds: Int
    let shelfReports: Int
    let weeks: Int
    let posts: Int
    let photoPosts: Int
    let saved: Int
    let badges: Int

    enum CodingKeys: String, CodingKey {
        case reports, farms, confirmations, weeks, posts, saved, badges
        case earlyBirds = "early_birds", shelfReports = "shelf_reports", photoPosts = "photo_posts"
    }
}

struct EarnedBadge: Decodable, Sendable {
    let badge: String
    let earnedAt: String
    enum CodingKeys: String, CodingKey { case badge, earnedAt = "earned_at" }
}

struct LeaderRow: Decodable, Identifiable, Sendable {
    let userId: String
    let name: String
    let reports: Int
    let farms: Int
    let confirmations: Int
    let badges: Int
    var id: String { userId }
    enum CodingKeys: String, CodingKey {
        case userId = "user_id", name, reports, farms, confirmations, badges
    }
}

@MainActor
@Observable
final class Contributions {
    static let shared = Contributions()
    private(set) var stats: ContributionStats?
    private(set) var earned: [EarnedBadge] = []
    /// Badges the last refresh awarded — the profile shows a celebration for
    /// these once, then forgets them.
    private(set) var justAwarded: [BadgeKind] = []
    private(set) var leaderboard: [LeaderRow] = []
    private(set) var leaderboardMonth: [LeaderRow] = []
    private init() {}

    func has(_ kind: BadgeKind) -> Bool { earned.contains { $0.badge == kind.rawValue } }
    func clearAwarded() { justAwarded = [] }

    func refreshMine(token: String) async {
        var request = URLRequest(url: Backend.webAPI.appending(path: "profile").appending(path: "contributions"))
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        guard let (data, resp) = try? await URLSession.shared.data(for: request),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let decoded = try? JSONDecoder().decode(MinePayload.self, from: data)
        else { return }
        stats = decoded.stats
        earned = decoded.badges
        justAwarded = decoded.awarded.compactMap(BadgeKind.init(rawValue:))
    }

    func refreshLeaderboard() async {
        async let all = fetchBoard(period: nil)
        async let month = fetchBoard(period: "month")
        leaderboard = await all
        leaderboardMonth = await month
    }

    private func fetchBoard(period: String?) async -> [LeaderRow] {
        var url = Backend.webAPI.appending(path: "community").appending(path: "leaderboard")
        if let period { url.append(queryItems: [URLQueryItem(name: "period", value: period)]) }
        guard let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let decoded = try? JSONDecoder().decode(BoardPayload.self, from: data)
        else { return [] }
        return decoded.rows
    }

    private struct MinePayload: Decodable { let stats: ContributionStats?; let badges: [EarnedBadge]; let awarded: [String] }
    private struct BoardPayload: Decodable { let rows: [LeaderRow] }
}
