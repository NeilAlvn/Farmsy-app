import Foundation
import CoreLocation
import Observation

// Turn a shopping list into trip stops.
//
// "Where can I buy this?" is the free map. "I need eggs, milk and potatoes —
// where do I actually drive?" is the thing a map cannot answer, and it is the
// reason someone plans a trip at all.
//
// Nothing here needs the network per farm: the pins are already in memory with
// their `produce` text (FarmsStore.produceByOsm, from /api/farms/flags), the
// geometry is TripGeometry, and the result is ordinary trip stops that the
// existing route drawing and Google Maps hand-off pick up unchanged.
//
// The one thing it fetches is the picker itself — GET /api/shopping/items —
// because the vocabulary is the part that keeps being wrong. `expandQuery`
// would answer "lamb" with beef, pork and chicken: right for a search, and
// wrong for a list in the way that matters. The web's `termsFor` narrows
// exactly those groups, so the terms arrive already expanded rather than being
// worked out again here.

// MARK: - Matching

/// How a word is compared with a farm's `produce` text.
///
/// Two rules, for two jobs, both ported from the web (src/lib/searchTerms.ts
/// and src/lib/shoppingList.ts) and both live bugs there before they were fixed:
///
///   `matches` — the SEARCH rule. A long term may sit inside a longer word, so
///   "kaas" finds "boerenkaas". Short ones may not, or `ui` finds `fruit`,
///   `tuin` and `uit`, and a search for onions returns a third of the map.
///
///   `covers`  — the LIST rule. Every term must be a whole word, because the
///   served terms already carry their own plurals. Onion once claimed 1,173
///   farms on the website that mostly sell fruit juice.
enum ProductMatch {

    /// Terms this short must match a whole word even under the search rule.
    static let shortTerm = 3

    /// Accents off, case off, hyphens and underscores to spaces.
    ///
    /// The product data is slugs — "pommes-de-terre", "hard-cheese" — while a
    /// farm writing prose says "pommes de terre", and "légumes" arrives spelled
    /// both ways. One folded form on both sides settles it in one place.
    static func fold(_ s: String) -> String {
        let lower = s.lowercased()
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: "_", with: " ")
        // ASCII fast path: almost every Dutch farm name is plain ASCII, and this
        // runs over thousands of them.
        guard lower.unicodeScalars.contains(where: { $0.value > 127 }) else { return lower }
        return lower.folding(options: .diacriticInsensitive, locale: nil)
    }

    private static func isWordChar(_ c: Character) -> Bool { c.isLetter || c.isNumber }

    /// Is `t` in `hay` as a word, rather than buried inside a longer one?
    static func hasWord(_ hay: String, _ t: String) -> Bool {
        guard !t.isEmpty else { return false }
        var from = hay.startIndex
        while let r = hay.range(of: t, range: from..<hay.endIndex) {
            let beforeOK = r.lowerBound == hay.startIndex
                || !isWordChar(hay[hay.index(before: r.lowerBound)])
            let afterOK = r.upperBound == hay.endIndex || !isWordChar(hay[r.upperBound])
            if beforeOK && afterOK { return true }
            guard r.lowerBound < hay.endIndex else { return false }
            from = hay.index(after: r.lowerBound)
        }
        return false
    }

    /// The SEARCH rule — does this farm's text answer any of these terms?
    static func matches(_ haystack: String, terms: [String]) -> Bool {
        guard !terms.isEmpty else { return false }
        let h = fold(haystack)
        return terms.contains { term in
            let f = fold(term)
            guard !f.isEmpty else { return false }
            return f.count <= shortTerm ? hasWord(h, f) : h.contains(f)
        }
    }

    /// The LIST rule — does this farm actually sell the thing, whole word only?
    ///
    /// Port of the web's `coverageOf`. The served terms already include the
    /// plural forms the data is inconsistent about, so nothing is gained by
    /// letting a term match inside a longer word, and a great deal is lost.
    static func covers(_ sells: String, terms: [String]) -> Bool {
        guard !terms.isEmpty, !sells.isEmpty else { return false }
        let h = fold(sells)
        return terms.contains { term in
            let f = fold(term)
            return f.count >= 2 && hasWord(h, f)
        }
    }
}

// MARK: - The picker

/// One thing a person can put on a list, as GET /api/shopping/items serves it.
struct ShoppingItem: Decodable, Identifiable, Equatable, Sendable {
    let id: String
    let nl: String
    let en: String
    /// Every word that means this, already narrowed and pluralised by the web.
    let terms: [String]
    /// The picker group (dairy, eggs, vegetables, …). Older servers omit it.
    let category: String?
    /// Slug of the bundled tile photograph. Older servers omit it; the id
    /// itself is the file name for every shopping item, so it falls back to that.
    let image: String?
    var imageSlug: String { image ?? id }

    /// Dutch or English, the same rule the website applies. fr and de fall back
    /// to English rather than showing an id — the labels only exist in two.
    var label: String {
        Locale.current.language.languageCode?.identifier == "nl" ? nl : en
    }

    /// A glyph for tiles and chips. Ids are the web's closed list; anything new
    /// falls back to the basket.
    var emoji: String {
        switch id {
        case "eggs": "🥚"; case "cheese": "🧀"; case "milk": "🥛"; case "potatoes": "🥔"
        case "vegetables": "🥬"; case "fruits": "🍎"; case "meat": "🥩"; case "honey": "🍯"
        case "bread": "🍞"; case "juices": "🧃"; case "jams": "🫙"; case "ice-cream": "🍦"
        case "strawberry": "🍓"; case "apples": "🍏"; case "pears": "🍐"; case "asparagus": "🌱"
        case "pumpkin": "🎃"; case "tomatoes": "🍅"; case "onions": "🧅"; case "carrot": "🥕"
        case "mushrooms": "🍄"; case "nuts": "🌰"; case "butter": "🧈"; case "yoghurt": "🥣"
        case "herbs": "🌿"; case "flowers": "🌷"; case "wine": "🍷"; case "beer": "🍺"
        case "fish": "🐟"
        default: "🧺"
        }
    }
}

extension ShoppingItem {
    /// Build an item in code — the picker uses fetched items, but tests and any
    /// call site that predates product photos want the four-field shape. Lives in
    /// an extension on purpose: it keeps the synthesized memberwise initializer
    /// (the full `category`/`image` form) *and* the synthesized `Decodable`, so a
    /// real item off `/api/shopping/items` still decodes both new fields. A stored
    /// default (`let image: String? = nil`) would make Swift silently drop them
    /// from the decode path; this keeps `category` and `image` as decoded `let`s.
    init(id: String, nl: String, en: String, terms: [String]) {
        self.init(id: id, nl: nl, en: en, terms: terms, category: nil, image: nil)
    }
}

/// How many farms near here sell a thing, and how close the nearest is.
struct ProductNearby: Identifiable {
    let item: ShoppingItem
    let count: Int
    let nearestKm: Double
    var id: String { item.id }
}

/// The picker list, fetched once.
@MainActor
@Observable
final class ShoppingItems {
    static let shared = ShoppingItems()

    private(set) var items: [ShoppingItem] = []
    private(set) var categories: [ShoppingCategory] = []
    private(set) var loadFailed = false
    private var loading: Task<Void, Never>?

    private init() {}

    func loadIfNeeded() async {
        if !items.isEmpty { return }
        if let loading { await loading.value; return }
        let task = Task { [weak self] in
            let url = Backend.webAPI.appending(path: "shopping").appending(path: "items")
            guard let (data, resp) = try? await URLSession.shared.data(from: url),
                  (resp as? HTTPURLResponse)?.statusCode == 200,
                  let decoded = try? JSONDecoder().decode(Payload.self, from: data)
            else {
                await MainActor.run { self?.loadFailed = true }
                return
            }
            await MainActor.run {
                self?.items = decoded.items
                self?.categories = decoded.categories ?? []
                self?.loadFailed = false
            }
        }
        loading = task
        await task.value
        loading = nil
    }

    func item(id: String) -> ShoppingItem? { items.first { $0.id == id } }

    private struct Payload: Decodable { let items: [ShoppingItem]; let categories: [ShoppingCategory]? }
}

/// A picker group, as the server orders them.
struct ShoppingCategory: Decodable, Identifiable, Equatable, Sendable {
    let id: String
    let nl: String
    let en: String
    let image: String
    var label: String { Locale.current.language.languageCode?.identifier == "nl" ? nl : en }
}

// MARK: - Planner

/// Which farms to visit for a shopping list, and what each one is for.
enum ShoppingPlanner {

    /// A farm as the planner needs it — deliberately not `FarmPin`, so this is
    /// a pure function a test can call without building a pin.
    struct Candidate {
        let osmId: String
        let coord: CLLocationCoordinate2D
        /// `produce`, or `produce_inferred` where the farm never told us.
        let sells: String
    }

    /// One stop, and the list items it answers (item ids, in the picked order).
    struct Pick: Equatable {
        let osmId: String
        let covers: [String]
    }

    struct Plan: Equatable {
        let picks: [Pick]
        /// Items nothing nearby sells. Shown, never hidden — "we found nothing
        /// for eggs" is the useful half of the answer.
        let missing: [String]
        var isEmpty: Bool { picks.isEmpty }
    }

    /// Greedy: repeatedly take the farm that answers the most still-unanswered
    /// items for the least extra driving.
    ///
    /// ponytail: greedy set cover, not optimal — a perfect answer is NP-hard and
    /// this runs over thousands of farms while someone waits. With five stops and
    /// a dozen items the gap to optimal is small enough not to be visible. The
    /// website goes further and offers three alternatives (most items / fewest
    /// stops / shortest drive); if that turns out to be the better screen here
    /// too, the honest move is to call its endpoint rather than port it.
    static func plan(
        wanted: [ShoppingItem],
        farms: [Candidate],
        origin: CLLocationCoordinate2D,
        maxStops: Int = 5,
        radiusKm: Double = 25
    ) -> Plan {
        guard !wanted.isEmpty else { return Plan(picks: [], missing: []) }
        let order = wanted.map(\.id)

        // Candidates inside the radius that answer at least one item.
        var covers: [(candidate: Candidate, items: Set<String>)] = []
        for farm in farms where !farm.sells.isEmpty {
            guard TripGeometry.haversineKm(origin, farm.coord) <= radiusKm else { continue }
            let hit = wanted.filter { ProductMatch.covers(farm.sells, terms: $0.terms) }.map(\.id)
            if !hit.isEmpty { covers.append((farm, Set(hit))) }
        }

        var remaining = Set(order)
        var picks: [Pick] = []
        var cursor = origin
        var used = Set<String>()

        while !remaining.isEmpty && picks.count < maxStops {
            var best: (candidate: Candidate, gained: Set<String>, score: Double)?
            for entry in covers where !used.contains(entry.candidate.osmId) {
                let gained = entry.items.intersection(remaining)
                if gained.isEmpty { continue }
                // Items answered per kilometre of extra driving. The +1 keeps a
                // farm on the doorstep from scoring infinitely better than a
                // farm one street further that answers twice as much.
                let km = TripGeometry.haversineKm(cursor, entry.candidate.coord)
                let score = Double(gained.count) / (1 + km)
                // Ties broken on osm_id so the same list always plans the same
                // trip — a route that reshuffles on every tap reads as broken.
                if best == nil || score > best!.score + 1e-9
                    || (abs(score - best!.score) <= 1e-9 && entry.candidate.osmId < best!.candidate.osmId) {
                    best = (entry.candidate, gained, score)
                }
            }
            guard let choice = best else { break }
            picks.append(Pick(osmId: choice.candidate.osmId,
                              covers: order.filter { choice.gained.contains($0) }))
            remaining.subtract(choice.gained)
            used.insert(choice.candidate.osmId)
            cursor = choice.candidate.coord
        }

        return Plan(picks: picks, missing: order.filter { remaining.contains($0) })
    }
}
