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
    /// Categories are multi-select, like the web filter — a farm matches if it is
    /// in any selected category. Empty means "all".
    var selectedCategories: Set<FarmCategory> = []

    // Quick filters — mirror the web's rail (Verified / Open now / Automaat /
    // Zelfpluk / Has photos). "Near me" is an action (locate), not a filter.
    var filterVerified = false
    var filterOpenToday = false
    var filterAutomaat = false
    var filterZelfpluk = false
    var filterHasPhotos = false

    // The two new axes from Aviah's taxonomy (multi-select, combine with the
    // categories rather than replacing them). Values are language-neutral ids; the
    // labels live client-side in FarmAxis. `organic` deliberately stays a category,
    // not a method, until its dual-coverage is resolved server-side.
    var selectedPlaceTypes: Set<String> = []
    var selectedMethods: Set<String> = []

    var anyQuickFilterOn: Bool {
        filterVerified || filterOpenToday || filterAutomaat || filterZelfpluk || filterHasPhotos
    }

    var anyFilterOn: Bool {
        anyQuickFilterOn || !selectedCategories.isEmpty
            || !selectedPlaceTypes.isEmpty || !selectedMethods.isEmpty
    }

    // AI search — the parsed intent currently in effect. When set, it drives the
    // filtering (products + categories + flags) and the summary bar shows what was
    // understood. Cleared by the bar's × or by any manual filter change.
    var aiIntent: SmartSearchIntent?
    /// Lowercase `produce` text per farm (the `p` flag), for matching AI product
    /// terms — loaded from the flags endpoint alongside the galleries.
    private(set) var produceByOsm: [String: String] = [:]
    /// Bumped when an AI search names a place, so the map can fly to it.
    private(set) var aiPlaceToken = 0
    private(set) var aiPlace: String?

    // Featured farms (What's New shelf): a farm's gallery photos from the public
    // flags endpoint, plus a *cached, once-shuffled* order so the shelf doesn't
    // reshuffle every time the sheet is opened. Both persist for the session.
    private(set) var galleries: [String: [String]] = [:]
    private(set) var galleriesLoaded = false
    private(set) var featuredOrder: [String] = []
    /// Prefetched description teasers for the featured farms, so the cards render
    /// instantly and the shelf can put farms *with* a description first.
    private(set) var featuredTeasers: [String: String] = [:]

    private struct FarmFlag: Decodable {
        let o: String; let g: [String]?; let p: String?
        let l: [String]?   // location_types (Type of place)
        let m: [String]?   // methods (How it is grown)
    }

    /// The flags endpoint decoded into the per-farm lookups the map needs
    /// (galleries, produce text, place-types, methods). One fetch, cached — the
    /// filters and product-matching read these without a second round trip. The
    /// heavier teaser prefetch stays in `loadGalleriesIfNeeded`.
    private(set) var flagsLoaded = false
    private(set) var locationTypesByOsm: [String: [String]] = [:]
    private(set) var methodsByOsm: [String: [String]] = [:]

    func loadFlagsIfNeeded() async {
        guard !flagsLoaded else { return }
        let url = Backend.webAPI.appending(path: "farms").appending(path: "flags")
        guard let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let rows = try? JSONDecoder().decode([FarmFlag].self, from: data)
        else { return }
        var map: [String: [String]] = [:]
        var produce: [String: String] = [:]
        var locs: [String: [String]] = [:]
        var meths: [String: [String]] = [:]
        for r in rows {
            if (r.g?.count ?? 0) >= 2 { map[r.o] = r.g }
            if let p = r.p, !p.isEmpty { produce[r.o] = p.lowercased() }
            if let l = r.l, !l.isEmpty { locs[r.o] = l }
            if let m = r.m, !m.isEmpty { meths[r.o] = m }
        }
        galleries = map
        produceByOsm = produce
        locationTypesByOsm = locs
        methodsByOsm = meths
        flagsLoaded = true
    }

    /// Fetch the galleries once, keep only farms with 2+ photos, prefetch their
    /// teasers, and freeze an order — farms that have a description first (so the
    /// top of the shelf always has one), shuffled within each group.
    func loadGalleriesIfNeeded() async {
        await loadFlagsIfNeeded()
        guard !galleriesLoaded else { return }
        let map = galleries

        // Prefetch teasers concurrently so the shelf can rank by "has description".
        let ids = Array(map.keys)
        var teasers: [String: String] = [:]
        await withTaskGroup(of: (String, String?).self) { group in
            for id in ids {
                group.addTask { (id, await FarmDetailAPI.teaser(osmId: id)?.text) }
            }
            for await (id, text) in group {
                if let text, !text.isEmpty { teasers[id] = text }
            }
        }
        featuredTeasers = teasers

        let described = ids.filter { teasers[$0] != nil }.shuffled()
        let rest = ids.filter { teasers[$0] == nil }.shuffled()
        featuredOrder = described + rest
        galleriesLoaded = true
    }

    /// The featured farms, in the frozen random order, resolved to pins.
    var featuredFarms: [FarmPin] {
        featuredOrder.compactMap { id in pins.first { $0.osmId == id } }
    }

    func clearAllFilters() {
        selectedCategories = []
        filterVerified = false; filterOpenToday = false
        filterAutomaat = false; filterZelfpluk = false; filterHasPhotos = false
        selectedPlaceTypes = []; selectedMethods = []
    }

    /// Apply a parsed AI intent — it takes over filtering and clears any manual
    /// filters so the two don't silently compound. Needs the produce map for
    /// product matching, so it makes sure the flags are loaded first.
    func applyAISearch(_ intent: SmartSearchIntent) async {
        await loadFlagsIfNeeded()
        clearAllFilters()
        aiIntent = intent
        if let place = intent.place, !place.isEmpty {
            aiPlace = place
            aiPlaceToken += 1
        } else {
            aiPlace = nil
        }
    }

    func clearAISearch() {
        aiIntent = nil
        aiPlace = nil
        searchText = ""
    }

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
            loadError = String(localized: "Couldn't load farms. Check your connection and try again.")
        }
    }

    /// Pins matching the current search + category + quick filters.
    var filtered: [FarmPin] {
        // AI search takes over when active — the parsed intent drives everything.
        if let ai = aiIntent {
            var result = pins
            let cats = Set(ai.categories.compactMap { FarmCategory.from($0) })
            let prods = ai.products.map { $0.lowercased() }
            // "What they sell": a farm matches its category OR its produce text —
            // OR'd so thin produce-field coverage doesn't drop farms the category
            // already accounts for.
            if !cats.isEmpty || !prods.isEmpty {
                result = result.filter { pin in
                    let catMatch = !cats.isEmpty && pin.categories.contains { cats.contains($0) }
                    let prodMatch = !prods.isEmpty
                        && (produceByOsm[pin.osmId].map { txt in prods.contains { txt.contains($0) } } ?? false)
                    return catMatch || prodMatch
                }
            }
            if ai.openNow  { result = result.filter { FarmFilters.isOpenToday($0.openingHours) } }
            if ai.verified { result = result.filter { $0.isVerified } }
            if ai.automaat { result = result.filter { FarmFilters.looksLikeAutomaat($0.name, openingHours: $0.openingHours) } }
            if ai.zelfpluk { result = result.filter { FarmFilters.looksLikeZelfpluk($0.name) } }
            return result
        }

        var result = pins
        if !selectedCategories.isEmpty {
            result = result.filter { pin in pin.categories.contains { selectedCategories.contains($0) } }
        }
        // Quick filters, same predicates the web applies (FarmFilters ports them).
        if filterVerified  { result = result.filter { $0.isVerified } }
        if filterOpenToday { result = result.filter { FarmFilters.isOpenToday($0.openingHours) } }
        if filterHasPhotos { result = result.filter { $0.image != nil } }
        if filterAutomaat  { result = result.filter { FarmFilters.looksLikeAutomaat($0.name, openingHours: $0.openingHours) } }
        if filterZelfpluk  { result = result.filter { FarmFilters.looksLikeZelfpluk($0.name) } }
        // The two new axes — a farm matches if any of its values is selected.
        if !selectedPlaceTypes.isEmpty {
            result = result.filter { pin in
                (locationTypesByOsm[pin.osmId] ?? []).contains { selectedPlaceTypes.contains($0) }
            }
        }
        if !selectedMethods.isEmpty {
            result = result.filter { pin in
                (methodsByOsm[pin.osmId] ?? []).contains { selectedMethods.contains($0) }
            }
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

    /// Farms within `radiusKm` that have at least one photo, nearest first.
    /// Powers the onboarding "farms near you" shelf (featured-card design).
    func nearbyWithImages(near coordinate: CLLocationCoordinate2D, radiusKm: Double = 100) -> [FarmPin] {
        let center = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        return pins
            .filter { $0.image != nil }
            .compactMap { pin in pin.distance(from: center).map { (pin, $0) } }
            .filter { $0.1 <= radiusKm * 1000 }
            .sorted { $0.1 < $1.1 }
            .map { $0.0 }
    }
}
