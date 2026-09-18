import SwiftUI

/// Haptics is the one switch Farmsy owns. Reduce Motion and text size are the
/// phone's, shown read-only so it is clear where to change them.
struct AccessibilitySheet: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(Haptics.key) private var haptics = true
    @Environment(\.dynamicTypeSize) private var typeSize

    private var reduceMotion: Bool { UIAccessibility.isReduceMotionEnabled }
    private var textSize: String {
        let cat = UIApplication.shared.preferredContentSizeCategory
        return cat == .large ? String(localized: "Default") : cat.rawValue.replacingOccurrences(of: "UICTContentSizeCategory", with: "")
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Accessibility"), compact: true, onBack: { dismiss() })
                .padding(.top, Space.s3)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: Space.s3) {
                    VStack(spacing: 0) {
                        Toggle(isOn: $haptics) {
                            HStack(spacing: Space.s3) {
                                Image(systemName: "iphone.radiowaves.left.and.right")
                                    .font(.system(size: 18, weight: .medium)).foregroundStyle(Color.farmGreen).frame(width: 24)
                                Text(String(localized: "Haptics")).role(.body)
                            }
                        }
                        .tint(Color.vivid)
                        .padding(.horizontal, Space.s4).padding(.vertical, Space.s3)
                        .onChange(of: haptics) { _, on in if on { Haptics.tap() } }
                    }
                    .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    Text(String(localized: "Turns off every vibration in Farmsy, including the tap when you report a farm."))
                        .role(.caption, .inkMuted)

                    Text(String(localized: "iOS settings")).role(.heading).padding(.top, Space.s4)
                    RowGroup {
                        Row(icon: "figure.walk", title: String(localized: "Reduce motion"),
                            value: reduceMotion ? String(localized: "On") : String(localized: "Off"), chevron: false) {}
                        Row(icon: "textformat.size", title: String(localized: "Text size"), value: textSize, chevron: false) {}
                        Row(icon: "gearshape", title: String(localized: "Open Settings")) {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                    }
                    Text(String(localized: "Farmsy follows these: it stops decorative motion and scales its type."))
                        .role(.caption, .inkMuted)
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s8)
            }
        }
        .background(Color.cream.ignoresSafeArea())
    }
}

/// One badge: the big circle, earned or locked, and the rule in words.
struct BadgeSheet: View {
    let kind: BadgeKind
    let earned: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Space.s4) {
            Image(systemName: kind.icon)
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(earned ? Color.ink : Color.inkFaint)
                .frame(width: 96, height: 96)
                .background(earned ? Color.vivid : Color.creamFill, in: Circle())
                .padding(.top, Space.s8)
            Text(kind.title).role(.heading)
            Badge(text: earned ? String(localized: "Earned") : String(localized: "Locked"),
                  fill: earned ? .vivid : .creamFill, ink: .ink)
            Text(kind.rule).role(.body, .inkMuted).multilineTextAlignment(.center)
                .padding(.horizontal, Space.s6)
            Spacer()
            Button(String(localized: "Done")) { dismiss() }
                .buttonStyle(PillButtonStyle(.primary, size: .medium, block: true))
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s6)
        }
        .frame(maxWidth: .infinity)
        .background(Color.cream.ignoresSafeArea())
    }
}
