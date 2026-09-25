import SwiftUI

/// A tap that does *not* fire when the finger was actually scrolling.
///
/// A plain `TapGesture`: the scroll view wins as soon as the finger moves, and
/// the tap only lands when it did not. This used to be a zero-distance
/// `DragGesture` run alongside the scroll, which on iOS 26 swallowed the
/// scroll instead — a swipe that started on a card went nowhere, so the
/// Discover farm list read as an app that had stopped responding.
///
/// `excludeTopTrailing` carves a square out of the top-right corner (for a save
/// heart or a close button that lives on the card), so a tap there doesn't also
/// fire the card's own action.
private struct TapActivate: ViewModifier {
    let action: () -> Void
    var excludeTopTrailing: CGFloat? = nil

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
            .onTapGesture { p in
                let inExcluded = excludeTopTrailing.map { s in p.x > size.width - s && p.y < s } ?? false
                guard !inExcluded else { return }
                Haptics.tap()
                action()
            }
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
