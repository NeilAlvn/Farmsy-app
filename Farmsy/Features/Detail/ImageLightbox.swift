import SwiftUI

/// What to show in the photo viewer. Identifiable so it drives a fullScreenCover.
struct LightboxSource: Identifiable {
    let id = UUID()
    let images: [String]
    var startIndex: Int = 0
    /// "Farm photo" or "From a post".
    var eyebrow: String
    /// The farm's name, or the post author's name.
    var title: String
    /// The farm's name under a post author, when the viewer names both.
    var subtitle: String? = nil
    /// The post's words, shown above the picture, clamped.
    var postText: String? = nil
}

/// The photo viewer (MOBILE-SPEC-MAP §6 / DETAIL §8). A fixed frame — it does not
/// resize to the picture, so the arrows stay put. A pale veil with a soft blur
/// behind it, not a dark screen; tapping outside closes.
struct ImageLightbox: View {
    let source: LightboxSource
    var onClose: () -> Void

    @State private var index: Int

    init(source: LightboxSource, onClose: @escaping () -> Void) {
        self.source = source
        self.onClose = onClose
        _index = State(initialValue: min(max(0, source.startIndex), max(0, source.images.count - 1)))
    }

    private var hasMany: Bool { source.images.count > 1 }

    var body: some View {
        ZStack {
            // A pale veil plus a blur, not a dark screen: a photograph does not
            // need the room darkened to be looked at. Tapping outside closes.
            Rectangle()
                .fill(.ultraThinMaterial)
                .overlay(Color.white.opacity(0.35))
                .ignoresSafeArea()
                .onTapGesture { onClose() }

            panel
                .frame(maxWidth: 480)
                .frame(height: 512) // 32rem, fixed
                .padding(16)
        }
    }

    private var panel: some View {
        VStack(spacing: 0) {
            header
            if let postText = source.postText, !postText.isEmpty {
                Text(postText)
                    .font(.geist(14))
                    .foregroundStyle(Color.ink)
                    .lineLimit(3)
                    .lineSpacing(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
            picture
        }
        .background(.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color.hairline, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.18), radius: 30, y: 12)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(source.eyebrow.uppercased())
                    .font(.geist(11, .semibold))
                    .kerning(1.1)
                    .foregroundStyle(Color.inkMuted)
                Text(source.title)
                    .font(.geist(14, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                if let subtitle = source.subtitle {
                    Text(subtitle)
                        .font(.geist(12))
                        .foregroundStyle(Color.farmGreenMap)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            if hasMany {
                Text("\(index + 1) / \(source.images.count)")
                    .font(.geist(12, .semibold))
                    .foregroundStyle(Color.inkMuted)
            }
            Button {
                Haptics.tap()
                onClose()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x6B7280))
                    .frame(width: 36, height: 36)
                    .background(Color(hex: 0xF3F4F6), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var picture: some View {
        ZStack {
            Color(hex: 0xF3F4F6)
            if let url = URL(string: source.images[safe: index] ?? "") {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        Image(systemName: "photo").font(.system(size: 40)).foregroundStyle(Color.inkMuted)
                    default:
                        ProgressView()
                    }
                }
            }
        }
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(alignment: .leading) {
            if hasMany { arrow("chevron.left") { step(-1) }.padding(.leading, 8) }
        }
        .overlay(alignment: .trailing) {
            if hasMany { arrow("chevron.right") { step(1) }.padding(.trailing, 8) }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    private func arrow(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color(hex: 0x374151))
                .frame(width: 40, height: 40)
                .background(.white.opacity(0.9), in: Circle())
                .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        }
        .buttonStyle(.plain)
    }

    private func step(_ delta: Int) {
        let n = source.images.count
        guard n > 0 else { return }
        withAnimation(.easeOut(duration: 0.24)) {
            index = (index + delta + n) % n   // wraps at both ends
        }
    }
}

private extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
