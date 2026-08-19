import Foundation

enum FarmDetailError: Error {
    case locked      // 401/403 — no active subscription on this account
    case notFound
    case other
}

/// Full farm detail comes only from the farmsy.app API, which verifies the
/// caller's subscription server-side. The public pins RPC never contains
/// these fields, so there is nothing to bypass on-device.
enum FarmDetailAPI {
    /// `/api/farm/[osmId]` is a single dynamic segment, but ~35% of osm_ids
    /// contain a slash (`node/123`, `amsterdam_urban/…`). `URL.appending(path:)`
    /// treats that slash as a path separator and breaks the route — the API then
    /// answers empty/404, which read as "descriptions don't load". Encode the id
    /// (slashes → %2F) and build the URL from the string instead.
    private static func farmURL(_ osmId: String, suffix: String = "") -> URL? {
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))
        let encoded = osmId.addingPercentEncoding(withAllowedCharacters: allowed) ?? osmId
        return URL(string: "\(Backend.webAPI.absoluteString)/farm/\(encoded)\(suffix)")
    }

    static func fetch(osmId: String, accessToken: String) async throws -> FarmDetail {
        guard let url = farmURL(osmId) else { throw FarmDetailError.other }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        switch status {
        case 200:
            return try JSONDecoder().decode(FarmDetail.self, from: data)
        case 401, 403:
            throw FarmDetailError.locked
        case 404:
            throw FarmDetailError.notFound
        default:
            throw FarmDetailError.other
        }
    }

    /// The opening of a farm's description, for someone without a membership.
    /// Public on purpose — a farm that has written about itself gets to say its
    /// first sentence to every visitor, which is the reason to unlock the rest.
    /// The full text stays behind the 403 on `fetch(osmId:)`. Best-effort: any
    /// failure yields nil and the card simply shows no teaser.
    static func teaser(osmId: String) async -> FarmTeaser? {
        guard let url = farmURL(osmId, suffix: "/teaser"),
              let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let teaser = try? JSONDecoder().decode(FarmTeaser.self, from: data),
              !teaser.text.isEmpty
        else { return nil }
        return teaser
    }

    private struct TeasersResponse: Decodable { let teasers: [String: FarmTeaser] }

    /// Teasers for many farms in one request — for rows/shelves, so a list doesn't
    /// spend a round trip per tile. POST (osm_ids go in the JSON body, sidestepping
    /// the `%2F` slash problem); the server caps the batch at 60. Every id sent
    /// comes back as a key; absent-or-empty both mean "nothing to show", so we drop
    /// blanks and return only the non-empty ones.
    static func teasers(osmIds: [String]) async -> [String: String] {
        guard !osmIds.isEmpty else { return [:] }
        let url = Backend.webAPI.appending(path: "farms/teasers")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONEncoder().encode(["osmIds": Array(osmIds.prefix(60))])

        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let decoded = try? JSONDecoder().decode(TeasersResponse.self, from: data)
        else { return [:] }

        return decoded.teasers.compactMapValues { $0.text.isEmpty ? nil : $0.text }
    }
}
