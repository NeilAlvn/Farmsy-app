import Foundation

/// Natural-language search parsing, via the web's `POST /api/search/smart`.
///
/// The endpoint reads a sentence ("waar kan ik aardbeien plukken bij Utrecht")
/// and returns a *structured intent* — products, categories, quick-filter flags,
/// an optional place, and a human summary in the query's own language. We apply
/// that intent to the pins we already hold; the only network call is the parse.
///
/// Contract notes from the backend (all deliberate):
/// - Values are validated against a closed vocabulary server-side, so it never
///   invents a product/category the data can't match — unknown terms are dropped.
/// - `summary` comes back in the language of the query; it must be shown so the
///   user sees what was understood.
/// - It fails to *empty*, never to an error: a 5s timeout or a bad response
///   returns every field empty. An all-empty result means "no intent parsed" —
///   the caller falls back to the plain keyword search it already has.
struct SmartSearchIntent: Decodable, Equatable {
    var products: [String] = []
    var categories: [String] = []
    var locationTypes: [String] = []    // Type-of-place axis (l)
    var methods: [String] = []          // How-it's-grown axis (m)
    var openNow: Bool = false
    var automaat: Bool = false
    var zelfpluk: Bool = false
    var verified: Bool = false
    var place: String? = nil
    var center: Center? = nil           // server-resolved coords — fly here
    var nearMe: Bool = false
    var radiusKm: Double? = nil
    var summary: String? = nil
    /// Server-supplied ranking weights — one source of truth, no coordinates on
    /// the wire, cache preserved. We score locally with these. Missing → fall back
    /// to `SearchRanking.default`; an unrecognised `version` is ignored the same way.
    var ranking: SearchRanking? = nil

    struct Center: Decodable, Equatable { let lat: Double; let lng: Double }

    /// True when the parse found nothing to act on — treat as "no intent".
    var isEmpty: Bool {
        products.isEmpty && categories.isEmpty
            && locationTypes.isEmpty && methods.isEmpty
            && !openNow && !automaat && !zelfpluk && !verified
            && !nearMe && center == nil && (place?.isEmpty ?? true)
    }
}

/// The result-ranking weights. Sent on the search response so all clients rank
/// identically without shipping releases; the defaults mirror v1 and are the
/// fallback when the field is absent.
struct SearchRanking: Decodable, Equatable {
    var version: Int = 1
    var distanceZeroKm: Double = 100
    var distanceWeight: Double = 100
    var openToday: Double = 20
    var verified: Double = 15
    var hasPhoto: Double = 10
    var ratingFactor: Double = 2
    var reviewEach: Double = 0.25
    var reviewCap: Double = 20

    static let `default` = SearchRanking()
}

enum SmartSearchAPI {
    static func parse(_ query: String) async -> SmartSearchIntent? {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return nil }
        let url = Backend.webAPI.appending(path: "search").appending(path: "smart")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["query": q])
        // Match the server's own 5s budget — a slower parse isn't worth the wait
        // when the keyword-search fallback is instant.
        request.timeoutInterval = 6

        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let intent = try? JSONDecoder().decode(SmartSearchIntent.self, from: data)
        else { return nil }
        return intent
    }
}
