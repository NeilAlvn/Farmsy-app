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
    /// Slug of the bundled tile photograph. Older servers omit it; most seasonal
    /// slugs are their own file, the aliased ones (aardbei → strawberry) show
    /// the emoji until the server sends the mapping.
    let image: String?
    var imageSlug: String { image ?? slug }
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

/// Copy in the four app languages, picked by the phone's language.
struct LocalizedText: Decodable, Sendable, Equatable {
    let nl: String, en: String, fr: String, de: String
    var text: String {
        switch Locale.current.language.languageCode?.identifier {
        case "nl": nl
        case "fr": fr
        case "de": de
        default: en
        }
    }
}

/// What to make with what is in season: one card, tied to the products it
/// needs so "put it on my list" is one tap.
struct SeasonIdea: Decodable, Identifiable, Sendable {
    let slug: String
    let month: Int
    let title: LocalizedText
    let body: LocalizedText
    /// Shopping ids or seasonal slugs.
    let ingredients: [String]
    let image: String
    var id: String { slug }
}

@MainActor
@Observable
final class Seasons {
    static let shared = Seasons()

    private(set) var month = Calendar.current.component(.month, from: Date())
    private(set) var items: [SeasonalItem] = []
    private(set) var ideas: [SeasonIdea] = []
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

    /// Everything in season in a month, peak first. Unlike `thisMonth` this
    /// keeps the year-round staples (eggs, cheese) out, so a month page is
    /// about what changed.
    func items(in month: Int) -> [SeasonalItem] {
        items.filter { $0.months.contains(month) && $0.months.count < 12 }
            .sorted { a, b in
                let pa = a.isPeak(month: month), pb = b.isPeak(month: month)
                return pa != pb ? pa : a.slug < b.slug
            }
    }

    func ideas(in month: Int) -> [SeasonIdea] { ideas.filter { $0.month == month } }

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
                self?.ideas = decoded.ideas ?? []
                self?.month = decoded.month
                self?.loaded = true
            }
        }
        loading = task
        await task.value
        loading = nil
    }

    private struct Payload: Decodable { let month: Int; let items: [SeasonalItem]; let ideas: [SeasonIdea]? }
}
