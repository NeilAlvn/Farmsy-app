import SwiftUI

/// A tap that does *not* fire when the finger was actually scrolling.
///
/// A plain `Button` inside a `ScrollView` fires on touch-up even when the touch
/// was the start of a scroll — which is why tapping-through-scroll opened farms
/// and photos by accident. This runs a zero-distance drag recogniser alongside
/// the scroll (so scrolling still works) and only calls the action when the
/// finger barely moved. Anything past the threshold is treated as a scroll and
/// the tap is dropped.
///
/// `excludeTopTrailing` carves a square out of the top-right corner (for a save
/// heart or a close button that lives on the card), so a tap there doesn't also
/// fire the card's own action.
private struct TapActivate: ViewModifier {
    let action: () -> Void
    var threshold: CGFloat = 12
    var excludeTopTrailing: CGFloat? = nil

    @State private var moved = false
    @State private var size: CGSize = .zero

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .background(
                GeometryReader { geo in
                    Color.clear
                        .onAppear { size = geo.size }
                        .onChange(of: geo.size) { _, s in size = s }
                }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        if abs(v.translation.width) > threshold || abs(v.translation.height) > threshold {
                            moved = true
                        }
                    }
                    .onEnded { v in
                        let inExcluded = excludeTopTrailing.map { s in
                            v.startLocation.x > size.width - s && v.startLocation.y < s
                        } ?? false
                        if !moved && !inExcluded {
                            Haptics.tap()
                            action()
                        }
                        moved = false
                    }
            )
    }
}

extension View {
    /// Tap-to-activate that ignores taps that were really scrolls. Use in place of
    /// a `Button` for tappable cards and images inside scroll views. Pass
    /// `excludeTopTrailing` to leave a corner control (a heart, a close) free.
    func tapCard(excludeTopTrailing: CGFloat? = nil, _ action: @escaping () -> Void) -> some View {
        modifier(TapActivate(action: action, excludeTopTrailing: excludeTopTrailing))
    }
}
