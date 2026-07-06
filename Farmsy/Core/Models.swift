import Foundation
import CoreLocation
import SwiftUI

// MARK: - Categories (mirrors the web map's taxonomy + pin colors)

enum FarmCategory: String, CaseIterable, Identifiable {
    case produce, dairy, cheese, eggs, meat, fish, honey, wine, markets, organic

    var id: String { rawValue }

    var emoji: String {
        switch self {
        case .produce: "🥬"
        case .dairy:   "🥛"
        case .cheese:  "🧀"
        case .eggs:    "🥚"
        case .meat:    "🥩"
        case .fish:    "🐟"
        case .honey:   "🍯"
        case .wine:    "🍷"
        case .markets: "🧺"
        case .organic: "🌱"
        }
    }

    var label: String {
        switch self {
        case .produce: "Farm Produce"
        case .dairy:   "Dairy"
        case .cheese:  "Cheese"
        case .eggs:    "Eggs"
        case .meat:    "Meat"
        case .fish:    "Fish"
        case .honey:   "Honey"
        case .wine:    "Wine"
        case .markets: "Markets"
        case .organic: "Organic"
        }
    }

    /// Same hex colors the web map uses for its pins.
    var color: Color {
        switch self {
        case .eggs:    Color(hex: 0xEAB308)
        case .dairy:   Color(hex: 0x38BDF8)
        case .meat:    Color(hex: 0xEF4444)
        case .fish:    Color(hex: 0x2563EB)
        case .produce: Color(hex: 0x10B981)
        case .cheese:  Color(hex: 0xF97316)
        case .wine:    Color(hex: 0x7C3AED)
        case .markets: Color(hex: 0x92400E)
        case .honey:   Color(hex: 0xD97706)
        case .organic: Color(hex: 0x059669)
        }
    }

    /// Same OSM-tag fallback mapping the web map applies when farm_type is empty.
    static let tagToCategory: [String: FarmCategory] = [
        "shop=farm": .produce, "shop=dairy": .dairy, "shop=cheese": .cheese,
        "craft=beekeeper": .honey, "shop=honey": .honey, "vending=eggs": .eggs,
        "vending=milk": .dairy, "tourism=wine_cellar": .wine, "craft=winery": .wine,
        "amenity=winery": .wine, "landuse=vineyard": .wine, "shop=farm (wine)": .wine,
        "shop=wine": .wine, "amenity=marketplace": .markets, "craft=cheesemaker": .cheese,
        "shop=butcher (farm)": .meat, "shop=bakery (farm)": .produce, "craft=butcher": .meat,
        "shop=butcher (direct_sale)": .meat, "shop=poultry": .meat, "vending=meat": .meat,
        "vending=sausage": .meat, "shop=fish": .fish, "craft=fish_farm": .fish,
    ]
}

// MARK: - Farm pin (public shape returned by the get_farms_pins RPC)

struct FarmPin: Identifiable, Hashable {
    let id: String
    let osmId: String
    let name: String
    let lat: Double
    let lng: Double
    let address: String?
    let city: String?
    let postalCode: String?
    let country: String?
    let phone: String?
    let website: String?
    let openingHours: String?
    let image: String?
    let primaryTag: String?
    let farmType: [String]
    let avgRating: Double?
    let reviewCount: Int
    let hasDescription: Bool

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    var categories: [FarmCategory] {
        var cats = farmType.compactMap { FarmCategory(rawValue: $0.lowercased()) }
        if cats.isEmpty, let tag = primaryTag, let mapped = FarmCategory.tagToCategory[tag] {
            cats = [mapped]
        }
        return cats
    }

    var primaryCategory: FarmCategory { categories.first ?? .produce }

    func distance(from location: CLLocation?) -> CLLocationDistance? {
        guard let location else { return nil }
        return CLLocation(latitude: lat, longitude: lng).distance(from: location)
    }
}

extension FarmPin: Decodable {
    enum CodingKeys: String, CodingKey {
        case id, osmId = "osm_id", name, lat, lng, address, city
        case postalCode = "postal_code", country, phone, website
        case openingHours = "opening_hours", image, primaryTag = "primary_tag"
        case farmType = "farm_type", avgRating = "avg_rating"
        case reviewCount = "review_count", hasDescription = "has_description"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        osmId = try c.decode(String.self, forKey: .osmId)
        name = try c.decode(String.self, forKey: .name)
        lat = try c.decode(Double.self, forKey: .lat)
        lng = try c.decode(Double.self, forKey: .lng)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        postalCode = try c.decodeIfPresent(String.self, forKey: .postalCode)
        country = try c.decodeIfPresent(String.self, forKey: .country)
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        website = try c.decodeIfPresent(String.self, forKey: .website)
        openingHours = try c.decodeIfPresent(String.self, forKey: .openingHours)
        image = try c.decodeIfPresent(String.self, forKey: .image)
        primaryTag = try c.decodeIfPresent(String.self, forKey: .primaryTag)
        avgRating = try c.decodeIfPresent(Double.self, forKey: .avgRating)
        reviewCount = try c.decodeIfPresent(Int.self, forKey: .reviewCount) ?? 0
        hasDescription = try c.decodeIfPresent(Bool.self, forKey: .hasDescription) ?? false
        // farm_type arrives as an array, a `{a,b}` postgres literal, a plain
        // string, or null depending on the source row — normalize like the web.
        if let arr = try? c.decodeIfPresent([String].self, forKey: .farmType) {
            farmType = arr.map { $0.lowercased() }
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .farmType), !str.isEmpty {
            if str.hasPrefix("{") && str.hasSuffix("}") {
                farmType = str.dropFirst().dropLast().split(separator: ",")
                    .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "\" ")).lowercased() }
            } else {
                farmType = [str.lowercased()]
            }
        } else {
            farmType = []
        }
    }
}

// MARK: - Farm detail (subscriber-gated payload from GET /api/farm/[osmId])

struct FarmDetail: Decodable {
    let osmId: String
    let phone: String?
    let website: String?
    let address: String?
    let postalCode: String?
    let country: String?
    let openingHours: String?
    let image: String?
    let description: String?
    let email: String?
    let facebook: String?
    let instagram: String?
    let organic: Bool?
    let produce: String?
    let operatorName: String?
    let images: [String]

    enum CodingKeys: String, CodingKey {
        case osmId = "osm_id", phone, website, address, postalCode = "postal_code"
        case country, openingHours = "opening_hours", image, description, email
        case facebook, instagram, organic, produce, operatorName = "operator", images
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        osmId = try c.decode(String.self, forKey: .osmId)
        phone = try? c.decodeIfPresent(String.self, forKey: .phone)
        website = try? c.decodeIfPresent(String.self, forKey: .website)
        address = try? c.decodeIfPresent(String.self, forKey: .address)
        postalCode = try? c.decodeIfPresent(String.self, forKey: .postalCode)
        country = try? c.decodeIfPresent(String.self, forKey: .country)
        openingHours = try? c.decodeIfPresent(String.self, forKey: .openingHours)
        image = try? c.decodeIfPresent(String.self, forKey: .image)
        description = try? c.decodeIfPresent(String.self, forKey: .description)
        email = try? c.decodeIfPresent(String.self, forKey: .email)
        facebook = try? c.decodeIfPresent(String.self, forKey: .facebook)
        instagram = try? c.decodeIfPresent(String.self, forKey: .instagram)
        // organic/produce column types vary — accept bool or string forms.
        if let b = try? c.decodeIfPresent(Bool.self, forKey: .organic) {
            organic = b
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .organic) {
            organic = ["yes", "true", "only", "organic"].contains(s.lowercased())
        } else {
            organic = nil
        }
        produce = try? c.decodeIfPresent(String.self, forKey: .produce)
        operatorName = try? c.decodeIfPresent(String.self, forKey: .operatorName)
        images = (try? c.decodeIfPresent([String].self, forKey: .images)) ?? []
    }
}

// MARK: - Profile (own row in profiles; RLS-protected)

struct Profile: Decodable {
    let subscriptionStatus: String?
    let subscriptionPlan: String?
    let subscriptionEndDate: Date?

    enum CodingKeys: String, CodingKey {
        case subscriptionStatus = "subscription_status"
        case subscriptionPlan = "subscription_plan"
        case subscriptionEndDate = "subscription_end_date"
    }

    /// Same rule as the web's isPaid(): active/trialing always pass, and a
    /// canceled plan keeps access until the already-paid period runs out.
    var hasFullAccess: Bool {
        switch subscriptionStatus {
        case "active", "trialing":
            return true
        case "canceled":
            if let end = subscriptionEndDate { return end > Date() }
            return false
        default:
            return false
        }
    }
}
