import SwiftUI

/// The free sample, wherever Farmsy's paid answer is withheld: the real
/// coverage sentence, and the real result blurred underneath it. Looking is
/// free, Farmsy doing the work is Plus — and there is never a padlock on an
/// empty screen, because the thing behind the blur is the honest answer.
///
/// Both the Shopping tab and the trip planner's "Shop from a list" sheet draw
/// their own rows through this, so the blur, the tap target and the
/// accessibility shape are decided once instead of twice (final review #1: the
/// sheet had no membership check at all and handed the named farms out for
/// free).
///
/// Accessibility (final review #4): `.accessibilityHidden(true)` and a label on
/// the SAME chain is not a labelled button — hidden wins and the block
/// disappears from VoiceOver entirely. So the blurred content alone is hidden,
/// and the container carries the element, the label and the button trait. Hit
/// testing is split the same way: the content takes no taps, the container is
/// the tap target, so tapping the block itself opens Plus. This mirrors
/// Android's `LockedSample` (`clearAndSetSemantics` inside, `clickable` +
/// `contentDescription` outside).
struct LockedSample<Content: View>: View {
    private let coverage: String
    private let label: String
    private let onUnlock: () -> Void
    private let content: Content

    init(coverage: String,
         label: String,
         onUnlock: @escaping () -> Void,
         @ViewBuilder content: () -> Content) {
        self.coverage = coverage
        self.label = label
        self.onUnlock = onUnlock
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            // Outside the tap target, so VoiceOver still reads the real number
            // instead of only "See which farms".
            Text(coverage).role(.heading)
            content
                .blur(radius: 7)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture { onUnlock() }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityAddTraits(.isButton)
        }
    }
}
