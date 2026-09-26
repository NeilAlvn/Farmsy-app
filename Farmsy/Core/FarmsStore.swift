import Foundation
import CoreLocation
import Observation
import Supabase

/// All public farm pins, loaded once via the get_farms_pins RPC (paginated
/// the same way the web map does) and filtered in memory.
@MainActor
@Observable
final class FarmsStore {
    private(set) var pins: [FarmPin] = [] {
        didSet { pinsById = Dictionary(pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a }) }
    }
    /// Built once per load. Home, Community and Trips each used to rebuild this
    /// dictionary of all 8,400 pins on every render, and `pin(forOsmId:)` was a
    /// linear scan.
    private(set) var pinsById: [String: FarmPin] = [:]
    private(set) var isLoading = false
    private(set) var loadError: String?

    var searchText = ""
    /// Categories are multi-select, like the web filter — a farm matches if it is
    /// in any selected category. Empty means "all".
    var selectedCategories: Set<FarmCategory> = [] {
        didSet { if oldValue != selectedCategories { onPreferencesChanged?() } }
    }

    // Quick filters — mirror the web's rail (Verified / Open now / Automaat /
    // Zelfpluk / Has photos). "Near me" is an action (locate), not a filter.
    var filterVerified = false  { didSet { if oldValue != filterVerified  { onPreferencesChanged?() } } }
    var filterOpenToday = false { didSet { if oldValue != filterOpenToday { onPreferencesChanged?() } } }
    var filterAutomaat = false
    var filterZelfpluk = false  { didSet { if oldValue != filterZelfpluk  { onPreferencesChanged?() } } }
    var filterHasPhotos = false { didSet { if oldValue != filterHasPhotos { onPreferencesChanged?() } } }

    // Pro time filters (Aviah's closed five-group set). Distinct from the FREE
    // `filterOpenToday` (day-based): these use the Amsterdam-clock parser —
    // `filterOpenNow` is open at this exact minute, the two day filters ask about a
    // specific weekday (Sat = Mon-based 5, Sun = 6). Gated on membership in the UI;
    // the predicates here are unconditional (the filter is only *set* when unlocked).
    var filterOpenNow = false
    var filterOpenSaturday = false
    var filterOpenSunday = false
    /// Plus: only farms a visitor reported open today. The set comes from
    /// RecentReports; the map hands it over so this store stays network-free.
    var filterConfirmedToday = false
    var confirmedTodayIds: Set<String> = []

    // The two new axes from Aviah's taxonomy (multi-select, combine with the
    // categories rather than replacing them). Values are language-neutral ids; the
    // labels live client-side in FarmAxis. `organic` deliberately stays a category,
    // not a method, until its dual-coverage is resolved server-side.
    var selectedPlaceTypes: Set<String> = []
    var selectedMethods: Set<String> = []

    // MARK: - Preferences (P0-3)

    /// Fires when one of the five persisted preference fields changes: the
    /// categories and the Verified / Open today / Pick-your-own / Has photos
    /// flags. PreferencesSync installs it; nothing else should.
    @ObservationIgnored var onPreferencesChanged: (() -> Void)?

    /// The five fields as the server stores them.
    var preferences: Preferences {
        Preferences(
            categories: FarmCategory.allCases.filter { selectedCategories.contains($0) }.map(\.rawValue),
            openToday: filterOpenToday, pickYourOwn: filterZelfpluk,
            verified: filterVerified, hasPhotos: filterHasPhotos)
    }

    /// Applies stored preferences. An empty category list means no category
    /// filter (every farm), and an unknown category is dropped, never an error.
    func apply(_ p: Preferences) {
        selectedCategories = p.knownCategories
        filterOpenToday = p.openToday
        filterZelfpluk = p.pickYourOwn
        filterVerified = p.verified
        filterHasPhotos = p.hasPhotos
    }

    var anyQuickFilterOn: Bool {
        filterVerified || filterOpenToday || filterAutomaat || filterZelfpluk || filterHasPhotos
    }

    /// Any of the five Pro groups active (three time filters + the two axis groups,
    /// which moved into Pro per Aviah's later-3). Used to show the "Pro filters active"
    /// state and to clear them when membership lapses.
    var anyProFilterOn: Bool {
        filterOpenNow || filterOpenSaturday || filterOpenSunday || filterConfirmedToday
            || !selectedPlaceTypes.isEmpty || !selectedMethods.isEmpty
    }

    var anyFilterOn: Bool {
        anyQuickFilterOn || anyProFilterOn || !selectedCategories.isEmpty
    }

    // AI search — the parsed intent currently in effect. When set, it drives the
    // filtering (products + categories + flags) and the summary bar shows what was
    // understood. Cleared by the bar's × or by any manual filter change.
    var aiIntent: SmartSearchIntent?
    /// Lowercase `produce` text per farm (the `p` flag), for matching AI product
    /// terms — loaded from the flags endpoint alongside the galleries.
    private(set) var produceByOsm: [String: String] = [:]
    /// Bumped when an AI search resolves a centre (server `center`, or the user's
    /// own location for a nearMe query), so the map can fly to it.
    private(set) var aiPlaceToken = 0
    /// The coordinate an AI search wants the map centred on, if any.
    private(set) var aiCenter: CLLocationCoordinate2D?
    /// Radius defaults when the query states no distance (Aviah's contract).
    private static let radiusNearMe = 15.0
    private static let radiusNamedPlace = 25.0

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

    /// The four lookups, built away from the main actor and handed back in one
    /// piece so the assignment below is a handful of stores rather than a loop.
    private struct FlagMaps: Sendable {
        var galleries: [String: [String]] = [:]
        var produce: [String: String] = [:]
        var locs: [String: [String]] = [:]
        var meths: [String: [String]] = [:]
    }

    /// `nonisolated` on purpose. This class is `@MainActor`, so until this was
    /// split out, decoding ~8,400 flag rows and building four dictionaries — a
    /// `lowercased()` per row among them — all ran on the main thread. Sentry
    /// recorded it as the app's single worst hang: 142 events across 35 users,
    /// every one of them preceded by a 200 from /api/search/smart, because
    /// `applyAISearch` awaits this before it can filter. The server was never
    /// slow; we blocked the UI decoding its answer. Android has always done this
    /// inside `withContext(Dispatchers.IO)`, so this restores parity too.
    private nonisolated static func decodeFlags(_ data: Data) -> FlagMaps? {
        guard let rows = try? JSONDecoder().decode([FarmFlag].self, from: data) else { return nil }
        var out = FlagMaps()
        for r in rows {
            if (r.g?.count ?? 0) >= 2 { out.galleries[r.o] = r.g }
            if let p = r.p, !p.isEmpty { out.produce[r.o] = p.lowercased() }
            if let l = r.l, !l.isEmpty { out.locs[r.o] = l }
            if let m = r.m, !m.isEmpty { out.meths[r.o] = m }
        }
        return out
    }

    /// The load in flight, so callers arriving together share one fetch.
    /// `flagsLoaded` only becomes true on the last line and there are two awaits
    /// before it, so without this two callers both pass the guard and both fetch
    /// and decode the whole payload. Moving the decode off the main actor made
    /// that worse rather than better: it turns one main-thread decode into two
    /// concurrent ones. Set before any await, so on the main actor it is atomic.
    @ObservationIgnored private var flagsTask: Task<Void, Never>?

    func loadFlagsIfNeeded() async {
        guard !flagsLoaded else { return }
        if let inFlight = flagsTask {
            await inFlight.value
            return
        }
        let task = Task { await self.loadFlags() }
        flagsTask = task
        await task.value
        flagsTask = nil
    }

    private func loadFlags() async {
        let url = Backend.webAPI.appending(path: "farms").appending(path: "flags")
        guard let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200
        else { return }
        guard let maps = await Task.detached(priority: .userInitiated, operation: {
            Self.decodeFlags(data)
        }).value else { return }
        galleries = maps.galleries
        produceByOsm = maps.produce
        locationTypesByOsm = maps.locs
        methodsByOsm = maps.meths
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
        featuredOrder.compactMap { pinsById[$0] }
    }

    /// Home's "Available near you": every list item with at least one farm in
    /// the radius that says it sells it, most farms first. Pure over the pins
    /// and the produce text, so it runs off the main thread.
    nonisolated static func productsNearby(items: [ShoppingItem], pins: [FarmPin], produce: [String: String],
                                           origin: CLLocationCoordinate2D, radiusKm: Double) -> [ProductNearby] {
        let here = CLLocation(latitude: origin.latitude, longitude: origin.longitude)
        let near: [(sells: String, km: Double)] = pins.compactMap { pin in
            guard let sells = produce[pin.osmId] else { return nil }
            let km = (pin.distance(from: here) ?? .infinity) / 1000
            return km <= radiusKm ? (sells, km) : nil
        }
        return items.compactMap { item in
            var count = 0
            var nearest = Double.infinity
            for farm in near where ProductMatch.covers(farm.sells, terms: item.terms) {
                count += 1
                nearest = min(nearest, farm.km)
            }
            return count > 0 ? ProductNearby(item: item, count: count, nearestKm: nearest) : nil
        }
        .sorted { $0.count > $1.count }
    }

    /// Show the map filtered on one product near the user — the same path an
    /// AI search takes, so ranking, radius and the summary bar come for free.
    func showProduct(label: String, terms: [String], userLocation: CLLocation?, radiusKm: Double) async {
        var intent = SmartSearchIntent()
        intent.products = terms
        intent.nearMe = userLocation != nil
        intent.radiusKm = radiusKm
        intent.summary = label
        await applyAISearch(intent, userLocation: userLocation)
    }

    func clearAllFilters() {
        selectedCategories = []
        filterVerified = false; filterOpenToday = false
        filterAutomaat = false; filterZelfpluk = false; filterHasPhotos = false
        filterOpenNow = false; filterOpenSaturday = false; filterOpenSunday = false
        filterConfirmedToday = false
        selectedPlaceTypes = []; selectedMethods = []
    }

    /// Apply a parsed AI intent — it takes over filtering and clears any manual
    /// filters so the two don't silently compound. Resolves the search centre:
    /// the server's `center` for a named place, or the user's own location for a
    /// nearMe query. Needs the flags maps for product/axis matching.
    func applyAISearch(_ intent: SmartSearchIntent, userLocation: CLLocation?) async {
        await loadFlagsIfNeeded()
        clearAllFilters()
        aiIntent = intent
        // Centre: prefer the server-resolved place centre; else the user's own
        // location when they meant "near me". Nil → no distance narrowing.
        if let c = intent.center {
            aiCenter = CLLocationCoordinate2D(latitude: c.lat, longitude: c.lng)
            aiPlaceToken += 1
        } else if intent.nearMe, let loc = userLocation {
            aiCenter = loc.coordinate
            aiPlaceToken += 1
        } else {
            aiCenter = nil
        }
    }

    /// The radius (metres) an active AI intent narrows to, or nil for no limit.
    private var aiRadiusMeters: Double? {
        guard let ai = aiIntent, aiCenter != nil else { return nil }
        let km = ai.radiusKm ?? (ai.nearMe ? Self.radiusNearMe : Self.radiusNamedPlace)
        return km * 1000
    }

    func clearAISearch() {
        aiIntent = nil
        aiCenter = nil
        searchText = ""
    }

    private static let pageSize = 1000

    func loadIfNeeded() async {
        guard pins.isEmpty, !isLoading else { return }
        isLoading = true
        loadError = nil
        defer { isLoading = false }

        do {
            // The first page also brings the total, so the other eight can go
            // out side by side. In a row they took about three seconds before
            // the map had a single pin; together, about one.
            let first: PostgrestResponse<[FarmPin]> = try await supabase
                .rpc("get_farms_pins")
                .range(from: 0, to: Self.pageSize - 1)
                .execute(options: FetchOptions(count: .exact))
            var all = first.value
            let total = first.count ?? all.count
            let rest = try await withThrowingTaskGroup(of: (Int, [FarmPin]).self) { group in
                for from in stride(from: Self.pageSize, to: total, by: Self.pageSize) {
                    group.addTask {
                        let page: [FarmPin] = try await supabase
                            .rpc("get_farms_pins")
                            .range(from: from, to: from + Self.pageSize - 1)
                            .execute()
                            .value
                        return (from, page)
                    }
                }
                var pages: [(Int, [FarmPin])] = []
                for try await page in group { pages.append(page) }
                return pages.sorted { $0.0 < $1.0 }
            }
            for (_, page) in rest { all.append(contentsOf: page) }
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
            let prods = ai.products.map { ProductMatch.fold($0) }
            // "What they sell": a farm matches its category OR its produce text —
            // OR'd so thin produce-field coverage doesn't drop farms the category
            // already accounts for.
            if !cats.isEmpty || !prods.isEmpty {
                result = result.filter { pin in
                    let catMatch = !cats.isEmpty && pin.categories.contains { cats.contains($0) }
                    let prodMatch = !prods.isEmpty
                        // Same rule as the shopping list: short terms must match a
                        // whole word, or `ui` finds every farm with `fruit` in its
                        // text. The web fixed this; the apps had not.
                        && (produceByOsm[pin.osmId].map { ProductMatch.matches($0, terms: prods) } ?? false)
                    return catMatch || prodMatch
                }
            }
            // The two new axes — any within an axis, both axes AND (same as chips).
            if !ai.locationTypes.isEmpty {
                let want = Set(ai.locationTypes)
                result = result.filter { (locationTypesByOsm[$0.osmId] ?? []).contains { want.contains($0) } }
            }
            if !ai.methods.isEmpty {
                let want = Set(ai.methods)
                result = result.filter { (methodsByOsm[$0.osmId] ?? []).contains { want.contains($0) } }
            }
            if ai.openNow  { result = result.filter { FarmFilters.isOpenToday($0.openingHours) } }
            if ai.verified { result = result.filter { $0.isVerified } }
            if ai.automaat { result = result.filter { FarmFilters.looksLikeAutomaat($0.name, openingHours: $0.openingHours) } }
            if ai.zelfpluk { result = result.filter { FarmFilters.looksLikeZelfpluk($0.name) } }
            // A place NARROWS: keep only farms within the radius of the centre.
            if let center = aiCenter, let radius = aiRadiusMeters {
                let origin = CLLocation(latitude: center.latitude, longitude: center.longitude)
                result = result.filter { ($0.distance(from: origin) ?? .infinity) <= radius }
            }
            // Rank once an intent is present — filtering says which qualify, ranking
            // says which to go to. Distance dominates when there's an origin.
            return rankForIntent(result, origin: aiCenter)
        }

        var result = pins
        if !selectedCategories.isEmpty {
            result = result.filter { pin in pin.categories.contains { selectedCategories.contains($0) } }
        }
        // Quick filters, same predicates the web applies (FarmFilters ports them).
        if filterVerified  { result = result.filter { $0.isVerified } }
        if filterOpenToday { result = result.filter { FarmFilters.isOpenToday($0.openingHours) } }
        // Pro time filters (Amsterdam clock). Saturday = Mon-based 5, Sunday = 6.
        if filterOpenNow      { result = result.filter { FarmFilters.isOpenNow($0.openingHours) } }
        if filterOpenSaturday { result = result.filter { FarmFilters.isOpenOnDay($0.openingHours, dayMon: 5) } }
        if filterOpenSunday   { result = result.filter { FarmFilters.isOpenOnDay($0.openingHours, dayMon: 6) } }
        if filterConfirmedToday { result = result.filter { confirmedTodayIds.contains($0.osmId) } }
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

    /// Rank AI-search results by usefulness — the web's signals: distance
    /// (dominant when there's an origin), then open today, verified, has a photo,
    /// rating, review count. One function so it can be swapped for a server-side
    /// order if the endpoint ever returns one (asked Aviah; matches her signal
    /// list until then). Higher score first.
    private func rankForIntent(_ list: [FarmPin], origin: CLLocationCoordinate2D?) -> [FarmPin] {
        // Weights come from the server (`ranking` on the response) so every client
        // ranks identically; fall back to the defaults if the field is absent or a
        // future `version` we don't recognise. Distance never leaves the device.
        let r = aiIntent?.ranking ?? .default
        let w = (r.version == SearchRanking.default.version) ? r : .default
        let zeroM = max(1, w.distanceZeroKm * 1000)
        let originLoc = origin.map { CLLocation(latitude: $0.latitude, longitude: $0.longitude) }
        func score(_ p: FarmPin) -> Double {
            var s = 0.0
            if let originLoc, let d = p.distance(from: originLoc) {
                s += max(0, 1 - d / zeroM) * w.distanceWeight
            }
            if FarmFilters.isOpenToday(p.openingHours) { s += w.openToday }
            if p.isVerified { s += w.verified }
            if p.image != nil { s += w.hasPhoto }
            s += (p.avgRating ?? 0) * w.ratingFactor
            s += min(Double(p.reviewCount), w.reviewCap) * w.reviewEach
            return s
        }
        return list.sorted { score($0) > score($1) }
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

    func pin(forOsmId osmId: String) -> FarmPin? { pinsById[osmId] }

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
