import Foundation
import Observation

// The seasonal calendar, as GET /api/seasons serves it.
//
// Served, not bundled: the table is hand-reviewed on the web and corrected
// there. The whole year comes down once and the month is picked here, so a
// month boundary needs no fetch. Until the endpoint is deployed the load
// fails quietly and the seasonal sections simply do not render.

struct SeasonalItem: Decodable, Identifiable, Sendable {
    let slug: String
    let nl: String
    let en: String
    let months: [Int]
    let peak: [Int]
    let category: String
    let storage: String?
    let note: String?
    /// Every word that means this, for matching a farm's produce text.
    let terms: [String]
    /// Available this month and not all year round.
    let now: Bool

    var id: String { slug }

    var label: String {
        Locale.current.language.languageCode?.identifier == "nl" ? nl : en
    }

    var emoji: String {
        switch category {
        case "dairy": "🥛"; case "eggs": "🥚"; case "meat": "🥩"; case "honey": "🍯"
        case "cheese": "🧀"; case "fish": "🐟"
        default: "🌱"
        }
    }

    func isPeak(month: Int) -> Bool { peak.contains(month) }
}

@MainActor
@Observable
final class Seasons {
    static let shared = Seasons()

    private(set) var month = Calendar.current.component(.month, from: Date())
    private(set) var items: [SeasonalItem] = []
    private(set) var loaded = false
    private var loading: Task<Void, Never>?

    private init() {}

    /// What is news this month: at peak first, then the rest that is in season.
    var thisMonth: [SeasonalItem] {
        let m = month
        return items.filter(\.now).sorted { a, b in
            let pa = a.isPeak(month: m), pb = b.isPeak(month: m)
            return pa != pb ? pa : a.slug < b.slug
        }
    }

    func loadIfNeeded() async {
        if loaded { return }
        if let loading { await loading.value; return }
        let task = Task { [weak self] in
            let url = Backend.webAPI.appending(path: "seasons")
            guard let (data, resp) = try? await URLSession.shared.data(from: url),
                  (resp as? HTTPURLResponse)?.statusCode == 200,
                  let decoded = try? JSONDecoder().decode(Payload.self, from: data)
            else { return }
            await MainActor.run {
                self?.items = decoded.items
                self?.month = decoded.month
                self?.loaded = true
            }
        }
        loading = task
        await task.value
        loading = nil
    }

    private struct Payload: Decodable { let month: Int; let items: [SeasonalItem] }
}
