import SwiftUI

/// A product photograph from the bundle, by the slug the server names.
///
/// The files are the 512px WebPs from farmsy-web/design/product-images,
/// dropped into Farmsy/ProductImages as product-<slug>.webp (Xcode flattens
/// them into the bundle root, hence the prefix), so a new image
/// is a file copy and nothing else. Decoded once and cached: the same eggs
/// appear on Home, Shopping and Discover in one scroll.
///
/// Falls back to the emoji the tile used to show, so a slug the bundle does
/// not have (a product added server-side before the next release) still
/// reads as something.
struct ProductImage: View {
    let slug: String?
    /// A second file to try before the emoji: a recipe picture that is not
    /// bundled yet shows its product's photograph.
    var fallbackSlug: String? = nil
    var fallback: String = "🌱"
    var size: CGFloat = 56
    var corner: CGFloat = Radius.thumb

    var body: some View {
        Group {
            if let image = slug.flatMap(ProductImageCache.image) ?? fallbackSlug.flatMap(ProductImageCache.image) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Text(fallback)
                    .font(.system(size: size * 0.5))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.creamFill)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}

enum ProductImageCache {
    private static var cache: [String: UIImage] = [:]
    private static var missing: Set<String> = []

    /// A seasonal product that is the same thing as a shopping item shares its
    /// photograph. The server sends the mapping as `image`; this is the same
    /// table (farmsy-web src/lib/productImages.ts) for builds that run against
    /// a server that does not yet.
    static let alias: [String: String] = [
        "aardbei": "strawberry", "appel": "apples", "peer": "pears", "asperge": "asparagus",
        "pompoen": "pumpkin", "tomaat": "tomatoes", "ui": "onions", "wortel": "carrot",
        "paddenstoel": "mushrooms", "aardappel": "potatoes", "honing": "honey",
        "eieren": "eggs", "kaas": "cheese",
    ]

    static func image(_ raw: String) -> UIImage? {
        let slug = alias[raw] ?? raw
        if let hit = cache[slug] { return hit }
        if missing.contains(slug) { return nil }
        guard let url = Bundle.main.url(forResource: "product-" + slug, withExtension: "webp"),
              let img = UIImage(contentsOfFile: url.path)
        else { missing.insert(slug); return nil }
        cache[slug] = img
        return img
    }
}
