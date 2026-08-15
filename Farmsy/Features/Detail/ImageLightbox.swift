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
    @State private var shown = false

    init(source: LightboxSource, onClose: @escaping () -> Void) {
        self.source = source
        self.onClose = onClose
        _index = State(initialValue: min(max(0, source.startIndex), max(0, source.images.count - 1)))
    }

    private var hasMany: Bool { source.images.count > 1 }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // A light veil — the surroundings stay recognisable, only softly
                // dimmed. It fades in place (pops), it does not slide. Tapping
                // outside closes.
                Color.white.opacity(0.55)
                    .background(.ultraThinMaterial)
                    .ignoresSafeArea()
                    .opacity(shown ? 1 : 0)
                    .onTapGesture { close() }

                // Bounded to the safe area so the frame never runs off the screen:
                // it takes the space available minus a margin, capped so it stays
                // a card rather than filling edge to edge.
                panel
                    .frame(width: min(geo.size.width - 32, 440),
                           height: min(geo.size.height - 64, 620))
                    .opacity(shown ? 1 : 0)
                    .scaleEffect(shown ? 1 : 0.94)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .onAppear { withAnimation(.easeOut(duration: 0.22)) { shown = true } }
    }

    private func close() {
        withAnimation(.easeIn(duration: 0.15)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { onClose() }
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
            // Counter and close aligned on one centred row, at the top of the header.
            HStack(spacing: 10) {
                if hasMany {
                    Text("\(index + 1) / \(source.images.count)")
                        .font(.geist(13, .semibold))
                        .foregroundStyle(Color.inkMuted)
                }
                Button {
                    Haptics.tap()
                    close()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280))
                        .frame(width: 36, height: 36)
                        .background(Color(hex: 0xF3F4F6), in: Circle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var picture: some View {
        // A base rectangle carries the size; the image is an overlay that fills
        // and is clipped — so the photo can never push past the container (which
        // was the overflow that hid the arrows).
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color(hex: 0xF3F4F6))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(
                Group {
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
            )
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
