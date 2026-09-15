import SwiftUI

struct PingCard: View {
    let ping: Ping
    let farmName: String?
    var onOpenFarm: () -> Void
    /// Tapping a photo opens the viewer at that index, rather than opening the farm.
    var onOpenImage: ((Int) -> Void)? = nil

    private var initials: String {
        let parts = ping.authorName.split(separator: " ").compactMap { $0.first }
        let s = String(parts.prefix(2)).uppercased()
        return s.isEmpty ? "?" : s
    }

    private var timeAgo: String {
        guard let date = ping.date else { return "" }
        let mins = Int(Date().timeIntervalSince(date) / 60)
        if mins < 1 { return String(localized: "just now") }
        if mins < 60 { return String(localized: "\(mins)m") }
        let hours = mins / 60
        if hours < 24 { return String(localized: "\(hours)h") }
        return String(localized: "\(hours / 24)d")
    }

    var body: some View {
        // Two tap regions, siblings not nested: the header + text open the farm,
        // the photos open the viewer. Keeping them separate avoids a nested-gesture
        // double-fire (opening the farm *and* the photo).
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Text(initials)
                        .font(.ui(14, .bold))
                        .foregroundStyle(Color.farmGreen)
                        .frame(width: 40, height: 40)
                        .background(Color.farmGreen.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 1) {
                        Text(ping.authorName)
                            .font(.ui(14, .semibold))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                        if let farmName {
                            Text(farmName)
                                .font(.ui(12, .medium))
                                .foregroundStyle(Color.farmGreenMap)
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 6)
                    Text(timeAgo)
                        .font(.ui(11))
                        .foregroundStyle(Color.inkMuted)
                }

                if !ping.body.isEmpty {
                    Text(ping.body)
                        .font(.ui(14))
                        .foregroundStyle(Color.ink)
                        .lineLimit(3)
                        .lineSpacing(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .contentShape(Rectangle())
            .tapCard(onOpenFarm)

            if !ping.images.isEmpty {
                FixedImageRow(urls: Array(ping.images.prefix(3)), height: 100,
                              onTap: onOpenImage)
            }

            HStack(spacing: 5) {
                Image(systemName: "heart")
                    .font(.system(size: 12))
                if ping.likeCount > 0 {
                    Text("\(ping.likeCount)").font(.ui(12))
                }
            }
            .foregroundStyle(Color.inkMuted)
            .contentShape(Rectangle())
            .tapCard(onOpenFarm)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.hairline, lineWidth: 1)
        )
    }
}

/// A row of equal fixed-size photo containers. Whatever a photo's aspect ratio,
/// it fills its slot and is clipped — so two photos are two equal squares that
/// never overlap or spill. Base rectangles carry the layout; the image is an
/// overlay, so the widths stay equal regardless of the images' own sizes.
struct FixedImageRow: View {
    let urls: [String]
    var height: CGFloat = 100
    /// Tapping a photo — passes its index. When nil the row is not tappable.
    var onTap: ((Int) -> Void)? = nil

    var body: some View {
        // Every photo is an equal tile of the given height. A single photo takes
        // one tile's width (half the row) rather than blowing up to a full-width
        // square — a lone image and one of two should read the same size.
        HStack(spacing: 6) {
            ForEach(Array(urls.enumerated()), id: \.element) { i, url in
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(hex: 0xF3F4F6))
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
                    .overlay(
                        AsyncImage(url: URL(string: url)) { phase in
                            if case .success(let img) = phase {
                                img.resizable().scaledToFill()
                            } else {
                                SkeletonBox(cornerRadius: 10)
                            }
                        }
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .modifier(OptionalTap(onTap: onTap.map { cb in { cb(i) } }))
            }
            // Pad a single photo out to one tile so it stays half-width.
            if urls.count == 1 { Color.clear.frame(maxWidth: .infinity).frame(height: height) }
        }
    }
}

/// Applies tapCard only when an action is provided, so a plain image row stays
/// non-interactive.
private struct OptionalTap: ViewModifier {
    let onTap: (() -> Void)?
    func body(content: Content) -> some View {
        if let onTap { content.tapCard(onTap) } else { content }
    }
}

/// A description clamped to three lines with a "View more" that does something
/// (opens the farm / the paywall), matching the web's clamp.
struct ClampedDescription: View {
    let text: String
    var lineLimit: Int = 3
    var onMore: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(text)
                .font(.ui(14))
                .foregroundStyle(Color.ink)
                .lineLimit(lineLimit)
                .lineSpacing(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            if text.count > 120 {
                Text("… View more")
                    .font(.ui(13, .semibold))
                    .foregroundStyle(Color.farmGreen)
            }
        }
    }
}
