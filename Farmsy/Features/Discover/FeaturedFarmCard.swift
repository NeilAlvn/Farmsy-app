import SwiftUI

/// A featured farm in the What's New shelf, laid out like a post: the first
/// gallery photo is the farm's round profile, then its name, city and the
/// opening of its description, then the rest of its photos in a fixed row.
struct MultiImageFarmCard: View {
    let pin: FarmPin
    let images: [String]
    /// The prefetched description (from the store) — no per-card fetch, so it's
    /// there on first render and the shelf can already be ordered by it.
    var teaser: String?
    var onOpen: () -> Void

    @Environment(FavoritesStore.self) private var favorites
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                // The farm's first photo, as a round profile (a copy — the
                // photo also stays in the row below).
                Circle()
                    .fill(Color(hex: 0xF3F4F6))
                    .frame(width: 40, height: 40)
                    .overlay(
                        AsyncImage(url: URL(string: images.first ?? "")) { phase in
                            if case .success(let img) = phase { img.resizable().scaledToFill() }
                            else { Text(pin.primaryCategory.emoji).font(.system(size: 18)) }
                        }
                    )
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(pin.name)
                        .font(.ui(14, .bold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    if let city = pin.city {
                        Text(city)
                            .font(.ui(12))
                            .foregroundStyle(Color.inkMuted)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                // Save heart, top-right — its own tap area, excluded from the
                // card's open action so saving doesn't also open the farm.
                Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                    .font(.system(size: 17))
                    .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.inkMuted)
                    .frame(width: 40, height: 40)
                    .contentShape(Circle())
                    .tapCard(toggleSave)
            }

            // Description (prefetched). Clamp to three lines; when it's cut, end
            // with a green "… View more" — tapping the card opens the farm.
            if let teaser, !teaser.isEmpty {
                (Text(teaser) + Text(teaser.count > 140 ? "  … View more" : "")
                    .foregroundColor(Color.farmGreen).bold())
                    .font(.ui(13))
                    .foregroundStyle(Color.ink)
                    .lineLimit(3)
                    .lineSpacing(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // All photos in a fixed row — including the first, which is also
            // the profile; we copy it here rather than dropping it.
            if !images.isEmpty {
                FixedImageRow(urls: Array(images.prefix(3)), height: 96)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.hairline, lineWidth: 1)
        )
        .tapCard(excludeTopTrailing: 52, onOpen)
    }

    private func toggleSave() {
        guard let userId = session.session?.user.id else { requestAuth(); return }
        Task { await favorites.toggle(pin.osmId, userId: userId) }
    }
}
