import Foundation

/// R5 — the corridor's product chips and the synonym lists behind them, loaded
/// from the bundled `product-vocabulary.json` (Aviah generates it from the web
/// source: shoppingList.ts + searchTerms.ts). The app never derives this — the
/// closed vocabulary is validated server-side and would drift if guessed — so the
/// file is the single source of truth, shared verbatim with Android.
///
/// Matching is the web's rule, not a substring `contains`: lowercase the farm's
/// product list, collapse every run of non-alphanumerics to a single space, pad
/// both ends with a space, and test for `" " + term + " "`. Whole word, so
/// "pear" never matches inside "spear" and "ei" (egg) never matches inside
/// "eind". The haystack is the product list only (`produce`, falling back to
/// `produce_inferred`) — never the description.
struct ProductChip: Decodable, Identifiable {
    let id: String
    let nl: String
    let en: String
    /// Every spelling/synonym that counts as this chip, already normalised by the
    /// generator (lowercase, single-spaced). A farm matches the chip if its
    /// normalised product text contains any of these as a whole word.
    let terms: [String]

    /// The label in the app's current language. The vocabulary ships nl + en; any
    /// other UI language falls back to English, which is the shared base.
    var label: String {
        Bundle.main.preferredLocalizations.first == "nl" ? nl : en
    }
}

enum ProductVocabulary {
    /// The 29 chips, in the commonality order the file lists them (top of the list
    /// is the useful end). Loaded once and cached.
    static let chips: [ProductChip] = load()

    private struct File: Decodable { let chips: [ProductChip] }

    private static func load() -> [ProductChip] {
        guard let url = Bundle.main.url(forResource: "product-vocabulary", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data)
        else { return [] }
        return file.chips
    }

    /// Normalise a farm's product text the web's way: lowercase, every run of
    /// non-alphanumerics → one space, padded with a leading and trailing space so
    /// a whole-word test is a plain substring check on `" term "`. Unicode letters
    /// and digits stay (é, ü count as alphanumeric); punctuation, hyphens and
    /// slashes become spaces.
    static func normalise(_ text: String) -> String {
        let collapsed = text.lowercased().unicodeScalars.map {
            CharacterSet.alphanumerics.contains($0) ? Character($0) : " "
        }
        // Collapse the runs of spaces we just introduced, then pad both ends.
        let joined = String(collapsed).split(separator: " ", omittingEmptySubsequences: true).joined(separator: " ")
        return " \(joined) "
    }

    /// True if the already-normalised (padded) haystack contains any of the chip's
    /// terms as a whole word.
    static func matches(_ chip: ProductChip, normalisedHaystack: String) -> Bool {
        chip.terms.contains { normalisedHaystack.contains(" \($0) ") }
    }
}
