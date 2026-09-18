import SwiftUI

/// A shimmering skeleton block — a grey base with a light band sweeping across,
/// so loading content reads as "arriving" rather than a static grey. Used for
/// photo tiles and cards while their images/data load.
struct SkeletonBox: View {
    var cornerRadius: CGFloat = 12
    @State private var phase: CGFloat = -1
    /// A pulsing block is decorative motion; under Reduce Motion it sits still.
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(Color(hex: 0xECEBE8))
            .overlay(
                GeometryReader { geo in
                    LinearGradient(
                        colors: [.clear, Color.white.opacity(0.55), .clear],
                        startPoint: .leading, endPoint: .trailing
                    )
                    .frame(width: geo.size.width * 0.6)
                    .offset(x: phase * geo.size.width * 1.4)
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}
