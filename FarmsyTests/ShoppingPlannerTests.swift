import Foundation
import CoreLocation
import Testing
@testable import Farmsy

/// The shopping list is only worth paying for if the trip it plans is one
/// someone would actually drive. Nothing here throws when it is wrong — a bad
/// plan is a plausible-looking list of the wrong farms — so the rules are
/// pinned: cover what you can, say what you could not, never wander.
///
/// The matching rule is pinned hardest, because both of its traps were live on
/// the website before they were fixed there: a short term inside a longer word,
/// and a search vocabulary used for a list.
struct ShoppingPlannerTests {

    /// Utrecht-ish. Everything below is placed relative to it in degrees, where
    /// 0.01° of latitude is about 1.1 km.
    static let origin = CLLocationCoordinate2D(latitude: 52.09, longitude: 5.12)

    static func at(_ dLat: Double, _ dLng: Double = 0) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: origin.latitude + dLat, longitude: origin.longitude + dLng)
    }

    /// Items as GET /api/shopping/items serves them — terms already narrowed
    /// and pluralised by the web, which is the whole reason they are served.
    static func item(_ id: String, _ terms: [String]) -> ShoppingItem {
        ShoppingItem(id: id, nl: id, en: id, terms: terms)
    }

    static let eggs = item("eggs", ["eggs", "egg", "eieren", "ei"])
    static let cheese = item("cheese", ["cheese", "kaas", "boerenkaas"])
    static let milk = item("milk", ["milk", "melk"])
    static let onions = item("onions", ["onions", "onion", "ui", "uien"])
    static let strawberry = item("strawberry", ["strawberry", "strawberries", "aardbei", "aardbeien"])
    /// The narrowed one. `expandQuery('lamb')` would also return beef and pork.
    static let lamb = item("lamb", ["lamb", "lamsvlees"])

    static func plan(_ wanted: [ShoppingItem], _ farms: [ShoppingPlanner.Candidate],
                     maxStops: Int = 5, radiusKm: Double = 25) -> ShoppingPlanner.Plan {
        ShoppingPlanner.plan(wanted: wanted, farms: farms, origin: origin,
                             maxStops: maxStops, radiusKm: radiusKm)
    }

    // ── The matching rules ──────────────────────────────────────────────────

    @Test("the list rule needs a whole word, for every term")
    func coverageIsWholeWordOnly() {
        // `ui` is inside `fruit`, `tuin` and `uit`. On the website onion once
        // claimed 1,173 farms that mostly sell fruit juice.
        #expect(!ProductMatch.covers("fruit, tuinplanten", terms: ["ui"]))
        #expect(ProductMatch.covers("ui, aardappelen", terms: ["ui"]))
        // Long terms too: the served terms already carry their plurals, so
        // nothing is gained by matching inside a longer word.
        #expect(!ProductMatch.covers("boerenkaas", terms: ["kaas"]))
        #expect(ProductMatch.covers("boerenkaas", terms: ["boerenkaas"]))
    }

    @Test("the search rule still finds a word inside a compound")
    func searchRuleKeepsSubstrings() {
        // Different rule, different job: searching "kaas" must find boerenkaas.
        #expect(ProductMatch.matches("boerenkaas en geitenkaas", terms: ["kaas"]))
        #expect(!ProductMatch.matches("fruit, tuinplanten", terms: ["ui"]))
        #expect(!ProductMatch.matches("prijslijst", terms: ["ijs"]))
    }

    @Test("accents and hyphens fold on both sides")
    func foldingBothWays() {
        #expect(ProductMatch.covers("Légumes de saison", terms: ["legumes"]))
        #expect(ProductMatch.covers("pommes-de-terre", terms: ["pommes de terre"]))
        #expect(ProductMatch.covers("hard-cheese", terms: ["cheese"]))
    }

    @Test("no terms covers nothing")
    func noTerms() {
        #expect(!ProductMatch.covers("eggs", terms: []))
        #expect(!ProductMatch.covers("", terms: ["eggs"]))
    }

    @Test("a narrowed item does not claim a farm selling its cousins")
    func narrowedVocabulary() {
        // The reason the terms are served rather than expanded on device: a
        // search for lamb may show a butcher, but a LIST that says your lamb is
        // covered by a beef farm is found out at the counter.
        let plan = Self.plan([Self.lamb], [
            .init(osmId: "beef-farm", coord: Self.at(0.01), sells: "beef, pork, chicken"),
        ])
        #expect(plan.isEmpty)
        #expect(plan.missing == ["lamb"])
    }

    // ── One stop is better than two ─────────────────────────────────────────

    @Test("a farm that sells everything is the only stop")
    func oneFarmCoversAll() {
        let plan = Self.plan([Self.eggs, Self.cheese], [
            .init(osmId: "a", coord: Self.at(0.02), sells: "eggs, cheese, milk"),
            .init(osmId: "b", coord: Self.at(0.03), sells: "eggs"),
        ])
        #expect(plan.picks.map(\.osmId) == ["a"])
        #expect(plan.picks.first?.covers == ["eggs", "cheese"])
        #expect(plan.missing.isEmpty)
    }

    @Test("two farms when one cannot cover the list, nearest useful first")
    func twoFarmsNeeded() {
        let plan = Self.plan([Self.eggs, Self.cheese], [
            .init(osmId: "eggs-close", coord: Self.at(0.01), sells: "eggs"),
            .init(osmId: "cheese-far", coord: Self.at(0.05), sells: "cheese"),
        ])
        #expect(plan.picks.map(\.osmId) == ["eggs-close", "cheese-far"])
        #expect(plan.picks.first?.covers == ["eggs"])
        #expect(plan.missing.isEmpty)
    }

    // ── Say what you could not find ─────────────────────────────────────────

    @Test("an item no farm sells is reported, not silently dropped")
    func missingReported() {
        let plan = Self.plan([Self.eggs, Self.strawberry], [
            .init(osmId: "a", coord: Self.at(0.01), sells: "eggs"),
        ])
        #expect(plan.picks.count == 1)
        #expect(plan.missing == ["strawberry"])
    }

    @Test("a farm that never said what it sells is never a stop")
    func silentFarmsSkipped() {
        let plan = Self.plan([Self.eggs], [
            .init(osmId: "silent", coord: Self.at(0.001), sells: ""),
            .init(osmId: "says-so", coord: Self.at(0.05), sells: "eggs"),
        ])
        #expect(plan.picks.map(\.osmId) == ["says-so"])
    }

    // ── Never wander ────────────────────────────────────────────────────────

    @Test("a farm beyond the radius is not offered, however well it fits")
    func radiusRespected() {
        // ~55 km north: outside the 25 km default.
        let plan = Self.plan([Self.eggs], [
            .init(osmId: "far", coord: Self.at(0.5), sells: "eggs, cheese, milk"),
        ])
        #expect(plan.isEmpty)
        #expect(plan.missing == ["eggs"])
    }

    @Test("the trip stops at maxStops even with more to buy")
    func maxStopsRespected() {
        let plan = Self.plan([Self.eggs, Self.cheese, Self.milk], [
            .init(osmId: "a", coord: Self.at(0.01), sells: "eggs"),
            .init(osmId: "b", coord: Self.at(0.02), sells: "cheese"),
            .init(osmId: "c", coord: Self.at(0.03), sells: "milk"),
        ], maxStops: 2)
        #expect(plan.picks.count == 2)
        #expect(plan.missing == ["milk"])
    }

    // ── The served terms are the point ──────────────────────────────────────

    @Test("Dutch finds English data")
    func crossLanguage() {
        let plan = Self.plan([Self.strawberry], [
            .init(osmId: "a", coord: Self.at(0.01), sells: "strawberry, asparagus"),
        ])
        #expect(plan.picks.map(\.osmId) == ["a"])
    }

    @Test("a short term does not drag in half the map")
    func shortTermDoesNotOverreach() {
        let plan = Self.plan([Self.onions], [
            .init(osmId: "fruit-farm", coord: Self.at(0.01), sells: "fruit, tuinplanten"),
            .init(osmId: "onion-farm", coord: Self.at(0.04), sells: "ui, aardappelen"),
        ])
        #expect(plan.picks.map(\.osmId) == ["onion-farm"])
    }

    // ── Same list, same trip ────────────────────────────────────────────────

    @Test("two identical farms plan the same way every time")
    func deterministicOnTies() {
        let farms: [ShoppingPlanner.Candidate] = [
            .init(osmId: "zzz", coord: Self.at(0.01), sells: "eggs"),
            .init(osmId: "aaa", coord: Self.at(0.01), sells: "eggs"),
        ]
        let first = Self.plan([Self.eggs], farms)
        let second = Self.plan([Self.eggs], farms.reversed())
        #expect(first == second)
        #expect(first.picks.map(\.osmId) == ["aaa"])
    }

    @Test("an empty list plans nothing rather than the whole map")
    func emptyList() {
        let plan = Self.plan([], [
            .init(osmId: "a", coord: Self.at(0.01), sells: "eggs"),
        ])
        #expect(plan.isEmpty)
        #expect(plan.missing.isEmpty)
    }
}
