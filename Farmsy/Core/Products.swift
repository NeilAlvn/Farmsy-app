import Foundation
import Observation

// The product profiles, as GET /api/products?lang= serves them: the year bar,
// how to choose, keep and preserve, five ideas, grandmother's tips, pairings.
// Researched with sources and read by a person before they ship; the app only
// shows them. One language per fetch, the phone's.

enum MonthState: String, Decodable, Sendable { case none, available, peak }

struct ProductProfile: Decodable, Identifiable, Sendable {
    struct Store: Decodable, Sendable { let place: String; let how: String; let days: Int; enum CodingKeys: String, CodingKey { case place = "where", how, days } }
    struct Preserve: Decodable, Sendable, Identifiable { let method: String; let how: String; var id: String { method + how } }
    struct Idea: Decodable, Sendable, Identifiable {
        let title: String; let body: String; let ingredients: [String]; let image: String
        var id: String { image }
    }
    let slug: String
    let image: String
    let shopping: String?
    let seasonal: String?
    let name: String
    let months: [MonthState]
    let regionNote: String
    let greenhouseNote: String
    let choose: [String]
    let store: Store
    let preserve: [Preserve]
    let ideas: [Idea]
    let tips: [String]
    let pairs: [String]
    let funFact: String
    var id: String { slug }

    enum CodingKeys: String, CodingKey {
        case slug, image, shopping, seasonal, name, months, choose, store, preserve, ideas, tips, pairs
        case regionNote = "region_note", greenhouseNote = "greenhouse_note", funFact = "fun_fact"
    }

    func state(in month: Int) -> MonthState { months.indices.contains(month - 1) ? months[month - 1] : .none }
}

@MainActor
@Observable
final class Products {
    static let shared = Products()
    private(set) var all: [ProductProfile] = []
    private(set) var loaded = false
    private var loading: Task<Void, Never>?
    private init() {}

    /// The profile for a shopping id or a seasonal slug, whichever the caller has.
    func profile(for slug: String) -> ProductProfile? {
        all.first { $0.slug == slug || $0.shopping == slug || $0.seasonal == slug }
    }

    func loadIfNeeded() async {
        if loaded { return }
        if let loading { await loading.value; return }
        let task = Task { [weak self] in
            let lang = Locale.current.language.languageCode?.identifier ?? "en"
            var url = Backend.webAPI.appending(path: "products")
            url.append(queryItems: [URLQueryItem(name: "lang", value: ["nl", "en", "fr", "de"].contains(lang) ? lang : "en")])
            let decoded = await (try? URLSession.shared.data(from: url)).flatMap { data, resp in
                (resp as? HTTPURLResponse)?.statusCode == 200 ? try? JSONDecoder().decode(Payload.self, from: data) : nil
            }
            // A failed fetch still ends the skeleton; the sheet says there is no profile yet.
            await MainActor.run { self?.all = decoded?.products ?? []; self?.loaded = true }
        }
        loading = task
        await task.value
        loading = nil
    }

    private struct Payload: Decodable { let products: [ProductProfile] }
}
