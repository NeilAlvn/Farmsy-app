import Foundation
import CoreLocation
import Observation

/// All public farm pins, loaded once via the get_farms_pins RPC (paginated
/// the same way the web map does) and filtered in memory.
@MainActor
@Observable
final class FarmsStore {
    private(set) var pins: [FarmPin] = []
    private(set) var isLoading = false
    private(set) var loadError: String?

    var searchText = ""
    var selectedCategory: FarmCategory?

    private static let pageSize = 1000

    func loadIfNeeded() async {
        guard pins.isEmpty, !isLoading else { return }
        isLoading = true
        loadError = nil
        defer { isLoading = false }

        var all: [FarmPin] = []
        var from = 0
        do {
            while true {
                let page: [FarmPin] = try await supabase
                    .rpc("get_farms_pins")
                    .range(from: from, to: from + Self.pageSize - 1)
                    .execute()
                    .value
                all.append(contentsOf: page)
                if page.count < Self.pageSize { break }
                from += Self.pageSize
            }
            pins = all
        } catch {
            loadError = "Couldn't load farms. Check your connection and try again."
        }
    }

    /// Pins matching the current search + category filter.
    var filtered: [FarmPin] {
        var result = pins
        if let cat = selectedCategory {
            result = result.filter { $0.categories.contains(cat) }
        }
        let query = searchText.trimmingCharacters(in: .whitespaces).lowercased()
        if !query.isEmpty {
            result = result.filter {
                $0.name.lowercased().contains(query)
                    || ($0.city?.lowercased().contains(query) ?? false)
                    || ($0.postalCode?.lowercased().contains(query) ?? false)
            }
        }
        return result
    }

    func sortedByDistance(_ list: [FarmPin], from location: CLLocation?) -> [FarmPin] {
        guard let location else { return list }
        return list.sorted {
            ($0.distance(from: location) ?? .infinity) < ($1.distance(from: location) ?? .infinity)
        }
    }

    /// Real category counts within a radius — powers the onboarding grid.
    func categoryCounts(near coordinate: CLLocationCoordinate2D, radiusKm: Double) -> [(FarmCategory, Int)] {
        let center = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        let nearby = pins.filter {
            ($0.distance(from: center) ?? .infinity) <= radiusKm * 1000
        }
        var counts: [FarmCategory: Int] = [:]
        for pin in nearby {
            for cat in pin.categories { counts[cat, default: 0] += 1 }
        }
        return FarmCategory.allCases
            .compactMap { cat in counts[cat].map { (cat, $0) } }
            .sorted { $0.1 > $1.1 }
    }

    func pin(forOsmId osmId: String) -> FarmPin? {
        pins.first { $0.osmId == osmId }
    }

    /// Random feed of farms that at least have a photo. When we know where
    /// the user is, farms within 75 km lead the feed (shuffled), with the
    /// rest of the countries shuffled in after.
    func feedPicks(near location: CLLocation?, limit: Int = 40) -> [FarmPin] {
        let withImage = pins.filter { $0.image != nil }
        guard let location else {
            return Array(withImage.shuffled().prefix(limit))
        }
        var nearby: [FarmPin] = []
        var rest: [FarmPin] = []
        for pin in withImage {
            if (pin.distance(from: location) ?? .infinity) <= 75_000 {
                nearby.append(pin)
            } else {
                rest.append(pin)
            }
        }
        return Array((nearby.shuffled() + rest.shuffled()).prefix(limit))
    }
}
