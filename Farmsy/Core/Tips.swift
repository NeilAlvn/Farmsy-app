import Foundation
import Observation

/// Grootmoeders tips, as GET /api/tips serves them: one card each on the
/// Discover tab, about a product, with its photograph and a list action.
struct Tip: Decodable, Identifiable, Sendable {
    let slug: String
    let kicker: LocalizedText
    let title: LocalizedText
    let body: LocalizedText
    let ingredient: String?
    let image: String
    var id: String { slug }
}

@MainActor
@Observable
final class Tips {
    static let shared = Tips()
    private(set) var tips: [Tip] = []
    private var loading: Task<Void, Never>?
    private init() {}

    func loadIfNeeded() async {
        if !tips.isEmpty { return }
        if let loading { await loading.value; return }
        let task = Task { [weak self] in
            let url = Backend.webAPI.appending(path: "tips")
            guard let (data, resp) = try? await URLSession.shared.data(from: url),
                  (resp as? HTTPURLResponse)?.statusCode == 200,
                  let decoded = try? JSONDecoder().decode(Payload.self, from: data)
            else { return }
            await MainActor.run { self?.tips = decoded.tips }
        }
        loading = task
        await task.value
        loading = nil
    }

    private struct Payload: Decodable { let tips: [Tip] }
}
