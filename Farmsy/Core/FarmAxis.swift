import SwiftUI

/// The two taxonomy axes Aviah split out of the category list — "Type of place"
/// (`location_types`) and "How it is grown" (`methods`). Values are the
/// language-neutral ids stored server-side (validated against a closed list);
/// labels live here, client-side, exactly like FarmCategory. They combine with
/// the category filters, they do not replace them.
///
/// `organic` is deliberately absent from `methods`: it sits on both axes right
/// now with 20× different coverage (1,420 farms as a category vs 71 as a method),
/// so it stays in the category rail until the backend resolves that.
struct FarmAxisValue: Identifiable, Hashable {
    let id: String        // the stored id, e.g. "milk-tap"
    let label: String     // localized display label
    let icon: String      // SF Symbol, to match the quick-filter rows
}

enum FarmAxis {
    /// `farms.location_types`.
    static let placeTypes: [FarmAxisValue] = [
        .init(id: "shop",                   label: String(localized: "Farm shop"),        icon: "storefront"),
        .init(id: "vending-machine",        label: String(localized: "Vending machine"),  icon: "cabinet"),
        .init(id: "stall",                  label: String(localized: "Roadside stall"),   icon: "basket"),
        .init(id: "milk-tap",               label: String(localized: "Milk tap"),         icon: "drop"),
        .init(id: "self-picking",           label: String(localized: "Self-picking"),     icon: "hand.raised"),
        .init(id: "self-picking-unstaffed", label: String(localized: "Self-picking (unstaffed)"), icon: "hand.raised.slash"),
    ]

    /// `farms.methods` — organic intentionally excluded (see the type doc).
    static let methods: [FarmAxisValue] = [
        .init(id: "biodynamic",   label: String(localized: "Biodynamic"),   icon: "moon.stars"),
        .init(id: "regenerative", label: String(localized: "Regenerative"), icon: "arrow.3.trianglepath"),
        .init(id: "grass-fed",    label: String(localized: "Grass-fed"),    icon: "leaf"),
        .init(id: "sustainable",  label: String(localized: "Sustainable"),  icon: "globe.europe.africa"),
    ]
}
