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
    var fallback: String = "🌱"
    var size: CGFloat = 56
    var corner: CGFloat = Radius.thumb

    var body: some View {
        Group {
            if let slug, let image = ProductImageCache.image(slug) {
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

    static func image(_ slug: String) -> UIImage? {
        if let hit = cache[slug] { return hit }
        if missing.contains(slug) { return nil }
        guard let url = Bundle.main.url(forResource: "product-" + slug, withExtension: "webp"),
              let img = UIImage(contentsOfFile: url.path)
        else { missing.insert(slug); return nil }
        cache[slug] = img
        return img
    }
}
