import Foundation
import Observation

/// A farm page address: /boerderijwinkels/<place>/<name>.
struct FarmPathRef: Equatable {
    let place: String
    let name: String
}

/// Farm page links (F3). The site claims /boerderijwinkels/*/* (and under
/// /en /fr /de /es) in apple-app-site-association, so tapping one opens the app
/// here. The pins carry no slug, and slugs move when a farm is renamed, so the
/// web turns the address into an osm_id (GET /api/farm/by-path, which follows
/// the slug history). No deferred deep link and no clipboard on iOS (spec A3).
enum FarmLink {
    private static let hosts: Set<String> = ["farmsy.app", "www.farmsy.app"]
    /// Dutch has no prefix; /nl/... is a redirect on the site and never claimed.
    private static let prefixedLocales: Set<String> = ["en", "fr", "de", "es"]

    /// Same rule as the web's isSegment.
    private static func isSegment(_ s: String) -> Bool {
        s.count <= 80 && s.range(of: "^[a-z0-9]+(-[a-z0-9]+)*$", options: .regularExpression) != nil
    }

    static func parse(_ url: URL) -> FarmPathRef? {
        guard url.scheme == "https", let host = url.host(), hosts.contains(host) else { return nil }
        var parts = url.pathComponents.filter { $0 != "/" }
        if let first = parts.first, prefixedLocales.contains(first) { parts.removeFirst() }
        guard parts.count == 3, parts[0] == "boerderijwinkels",
              isSegment(parts[1]), isSegment(parts[2]) else { return nil }
        return FarmPathRef(place: parts[1], name: parts[2])
    }

    /// Where the link came from, for `app_opened_from_link`: a QR poster, a web button, or a plain link.
    static func source(of url: URL) -> String {
        let value = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "utm_source" })?.value
        return (value?.isEmpty == false ? value : nil) ?? "link"
    }

    static func lookupURL(_ ref: FarmPathRef, base: URL = Backend.webAPI) -> URL? {
        var c = URLComponents(url: base.appending(path: "farm/by-path"), resolvingAgainstBaseURL: false)
        c?.queryItems = [URLQueryItem(name: "place", value: ref.place), URLQueryItem(name: "name", value: ref.name)]
        return c?.url
    }

    private struct ByPath: Decodable {
        let osmId: String
        enum CodingKeys: String, CodingKey { case osmId = "osm_id" }
    }

    /// nil on anything but a 200: offline, unknown farm, rate-limited. The app
    /// then simply stays where it is — opening the page in Safari would loop
    /// straight back into the app.
    static func osmId(for ref: FarmPathRef) async -> String? {
        guard let url = lookupURL(ref),
              let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let body = try? JSONDecoder().decode(ByPath.self, from: data)
        else { return nil }
        return body.osmId
    }
}

/// A farm a link asked for. AppShell opens it once its pin is loaded, then clears it
/// — the same hand-off as `PushRegistrar.pendingOsmId` for a notification tap.
@MainActor
@Observable
final class FarmLinkInbox {
    static let shared = FarmLinkInbox()
    var pendingOsmId: String?
    private init() {}
}
