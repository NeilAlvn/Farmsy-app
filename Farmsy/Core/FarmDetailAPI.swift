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
}
