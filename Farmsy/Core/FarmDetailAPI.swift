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
    static func fetch(osmId: String, accessToken: String) async throws -> FarmDetail {
        var request = URLRequest(
            url: Backend.webAPI.appending(path: "farm").appending(path: osmId)
        )
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
        let url = Backend.webAPI
            .appending(path: "farm").appending(path: osmId).appending(path: "teaser")
        guard let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let teaser = try? JSONDecoder().decode(FarmTeaser.self, from: data),
              !teaser.text.isEmpty
        else { return nil }
        return teaser
    }
}
