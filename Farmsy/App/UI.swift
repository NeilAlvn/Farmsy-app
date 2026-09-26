import SwiftUI

// The primitive kit, measured from Nime's `src/components/ui/*` and drawn with
// Farmsy's tokens. One file on purpose: every screen reads it, and the pieces
// are small enough that a file each would be more navigation than code.

// MARK: - Floating tab bar

/// Detached 64pt pill above the home indicator, icons only (labels are for
/// accessibility). Active = ink + filled symbol, inactive = muted + outline.
struct FloatingTabBar: View {
    @Binding var selected: AppTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { tab in
                let on = tab == selected
                Button {
                    if !on { Haptics.tap() }
                    selected = tab
                } label: {
                    Image(on ? tab.filledIcon : tab.icon)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 26, height: 26)
                        .foregroundStyle(on ? Color.ink : Color.inkMuted)
                        .frame(width: 56, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.title)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(.horizontal, Space.s3)
        .frame(height: TabBarInset.height)
        .background(Color.surface, in: Capsule())
        .overlay(Capsule().stroke(Color.hairline, lineWidth: 1))
        .shadow(color: Color.ink.opacity(0.10), radius: 12, y: 8)
    }
}

// MARK: - Header

/// Two forms. Large: 28pt title left, icon buttons right — tab roots. Compact:
/// 52pt bar with a back button in a fixed side slot and a centred title — every
/// pushed or sheet screen.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    var compact = false
    var onBack: (() -> Void)? = nil
    var onAtmosphere = false
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, compact: Bool = false, onBack: (() -> Void)? = nil,
         onAtmosphere: Bool = false, @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }) {
        self.title = title
        self.compact = compact
        self.onBack = onBack
        self.onAtmosphere = onAtmosphere
        self.trailing = trailing
    }

    private var tone: Color { onAtmosphere ? .white : .ink }

    var body: some View {
        if compact {
            ZStack {
                Text(title).role(.subheading, tone).lineLimit(1)
                    .padding(.horizontal, 88)
                HStack(spacing: Space.s2) {
                    if let onBack {
                        IconButton("chevron.left", label: String(localized: "Back"),
                                   overMedia: onAtmosphere, action: onBack)
                    }
                    Spacer()
                    trailing()
                }
            }
            .frame(minHeight: 52)
            .padding(.horizontal, Space.s4)
        } else {
            HStack(alignment: .center, spacing: Space.s2) {
                Text(title).role(.title, tone)
                Spacer()
                trailing()
            }
            .padding(.horizontal, Space.s4)
            .padding(.top, Space.s2)
            .padding(.bottom, Space.s3)
        }
    }
}

/// Section header inside a scroll: heading left, optional text action right.
struct SectionHeader: View {
    let title: String
    var action: (label: String, run: () -> Void)? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).role(.heading)
            Spacer()
            if let action {
                Button(action.label) { Haptics.tap(); action.run() }
                    .buttonStyle(PillButtonStyle(.text, size: .small))
            }
        }
        .padding(.top, Space.s6)
        .padding(.bottom, Space.s3)
    }
}

// MARK: - Buttons

enum PillVariant { case primary, secondary, ghost, soft, destructive, text, surface }
enum PillSize {
    case large, medium, small
    var height: CGFloat { switch self { case .large: 56; case .medium: 48; case .small: 40 } }
}

/// The one button. `.primary` is ink; `.secondary` is the vivid green with ink
/// text; `.ghost` is a hairline; `.soft` is the accent wash; `.text` is a link.
struct PillButtonStyle: ButtonStyle {
    var variant: PillVariant = .primary
    var size: PillSize = .large
    var block = false

    init(_ variant: PillVariant = .primary, size: PillSize = .large, block: Bool = false) {
        self.variant = variant
        self.size = size
        self.block = block
    }

    private var fill: Color {
        switch variant {
        case .primary: .ink
        case .secondary: .vivid
        case .ghost, .text: .clear
        case .soft: .farmGreenSoft
        case .destructive: .critical
        case .surface: .surface
        }
    }

    private var label: Color {
        switch variant {
        case .primary, .destructive: .white
        case .secondary, .ghost, .surface, .soft: .ink
        case .text: .farmGreen
        }
    }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(variant == .text && size == .small ? 15 : 17, .semibold))
            // One line that shrinks rather than wraps or truncates. Without these a
            // label too wide for its button either wrapped to two lines — which
            // changes the button's height and looks accidental next to a one-line
            // sibling — or was cut mid-word. It bites hardest in nl/fr/de, where the
            // same label is longer, and on any block/weighted button that cannot
            // grow. 0.7 keeps it legible; below that the copy is too long, not the
            // button too small. Mirrors Android's FitText.
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .foregroundStyle(label)
            .frame(maxWidth: block ? .infinity : nil,
                   minHeight: variant == .text ? nil : size.height)
            .padding(.horizontal, variant == .text ? 0 : Space.s6)
            .background(fill, in: Capsule())
            .overlay(Capsule().stroke(Color.hairline, lineWidth: variant == .ghost ? 1 : 0))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed && variant != .text ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// 44pt circle (40 when `small`). On the canvas: white with a hairline. Over a
/// photo or the atmosphere band: white at 92% with a floating shadow.
struct IconButton: View {
    let systemName: String
    let label: String
    var overMedia = false
    var small = false
    var selected = false
    let action: () -> Void

    init(_ systemName: String, label: String, overMedia: Bool = false, small: Bool = false,
         selected: Bool = false, action: @escaping () -> Void) {
        self.systemName = systemName
        self.label = label
        self.overMedia = overMedia
        self.small = small
        self.selected = selected
        self.action = action
    }

    var body: some View {
        Button { Haptics.tap(); action() } label: {
            Image(systemName: selected ? systemName + ".fill" : systemName)
                .font(.system(size: small ? 18 : 20, weight: .medium))
                .foregroundStyle(Color.ink)
                .frame(width: small ? 40 : 44, height: small ? 40 : 44)
                .background(overMedia ? Color.white.opacity(0.92) : Color.surface, in: Circle())
                .overlay(Circle().stroke(Color.hairline, lineWidth: overMedia ? 0 : 1))
                .shadow(color: Color.ink.opacity(overMedia ? 0.10 : 0), radius: 12, y: 8)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// MARK: - Chip and badge

/// 36pt pill. Selected = ink fill; a `dot` colour draws an 8pt fill-only state
/// dot (open / uncertain / closed) before the label.
struct Chip: View {
    let label: String
    var icon: String? = nil
    var emoji: String? = nil
    var selected = false
    var dot: Color? = nil
    let action: () -> Void

    var body: some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 6) {
                if let dot { Circle().fill(dot).frame(width: 8, height: 8) }
                if let emoji { Text(emoji).font(.system(size: 14)) }
                if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)) }
                Text(label).font(.ui(15, .medium)).lineLimit(1)
            }
            .foregroundStyle(selected ? .white : Color.ink)
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(selected ? Color.ink : Color.surface, in: Capsule())
            .overlay(Capsule().stroke(Color.hairline, lineWidth: selected ? 0 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 22pt pill label. Accent by default; pass a vivid `fill` for a state badge.
struct Badge: View {
    let text: String
    var fill: Color = .farmGreen
    var ink: Color = .white

    var body: some View {
        Text(text)
            .font(.ui(12, .semibold))
            .tracking(0.3)
            .foregroundStyle(ink)
            .padding(.horizontal, Space.s2)
            .frame(height: 22)
            .background(fill, in: Capsule())
    }
}

// MARK: - Grouped rows

/// A white radius-20 container with hairline dividers inset by 16.
struct RowGroup<Content: View>: View {
    var title: String? = nil
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            if let title { Text(title).role(.label, .inkFaint).textCase(.uppercase) }
            VStack(spacing: 0) {
                _VariadicView.Tree(_Divided()) { content() }
            }
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
    }
}

/// Puts a hairline between each pair of children.
private struct _Divided: _VariadicView.MultiViewRoot {
    @ViewBuilder
    func body(children: _VariadicView.Children) -> some View {
        let last = children.last?.id
        ForEach(children) { child in
            child
            if child.id != last {
                Divider().overlay(Color.hairline).padding(.leading, Space.s4)
            }
        }
    }
}

/// 56pt row: leading symbol, title + optional subtitle, trailing value or chevron.
struct Row: View {
    let icon: String
    let title: String
    var subtitle: String? = nil
    var value: String? = nil
    var chevron = true
    var tint: Color = .ink
    let action: () -> Void

    var body: some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: Space.s3) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(tint == .ink ? Color.farmGreen : tint)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).role(.body, tint)
                    if let subtitle { Text(subtitle).role(.caption, .inkMuted) }
                }
                Spacer(minLength: Space.s2)
                if let value {
                    Text(value).role(.body, .inkMuted).lineLimit(1)
                        .frame(maxWidth: 160, alignment: .trailing)
                }
                if chevron {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.inkMuted)
                }
            }
            .padding(.horizontal, Space.s4)
            .padding(.vertical, 10)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowPressStyle())
    }
}

/// Pressed rows show the tile colour instead of dimming.
struct RowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.creamFill : Color.clear)
    }
}

// MARK: - Search field

/// 48pt pill on the tile colour. `onAtmosphere` draws it white at 92%.
struct SearchField: View {
    @Binding var text: String
    var placeholder: String
    var onAtmosphere = false
    var onSubmit: () -> Void = {}

    var body: some View {
        HStack(spacing: Space.s2) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color.inkMuted)
            TextField(placeholder, text: $text)
                .font(.ui(16))
                .foregroundStyle(Color.ink)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit(onSubmit)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(Color.inkMuted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Clear search"))
            }
        }
        .padding(.horizontal, Space.s4)
        .frame(height: 48)
        .background(onAtmosphere ? Color.white.opacity(0.92) : Color.creamFill, in: Capsule())
    }
}

// MARK: - Empty state

struct EmptyState: View {
    let icon: String
    let title: String
    let text: String
    var action: (label: String, run: () -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: icon)
                .font(.system(size: 44, weight: .regular))
                .foregroundStyle(Color.farmGreen)
            Text(title).role(.heading).multilineTextAlignment(.center).padding(.top, Space.s4)
            Text(text).role(.body, .inkMuted).multilineTextAlignment(.center).padding(.top, Space.s2)
            if let action {
                Button(action.label) { action.run() }
                    .buttonStyle(PillButtonStyle(.primary, size: .medium))
                    .padding(.top, Space.s6)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Space.s6)
        .padding(.vertical, Space.s12)
    }
}

// MARK: - Atmosphere band

/// The one gradient in the system: accent fading into the canvas with a hard
/// stop, behind the Home header. Reads as a horizon, not a hero.
struct AtmosphereBand: View {
    var stop: CGFloat = 0.72

    var body: some View {
        LinearGradient(
            stops: [
                .init(color: .farmGreen, location: 0),
                .init(color: .farmGreen, location: stop),
                .init(color: .cream, location: 1),
            ],
            startPoint: .top, endPoint: .bottom
        )
    }
}

/// The `farmsy` wordmark: the display size of the type scale, bold, tight.
struct Wordmark: View {
    var onAtmosphere = false
    var size: CGFloat = 22
    var body: some View {
        Text(verbatim: "farmsy")
            .font(.ui(size, .bold))
            .tracking(-0.5)
            .foregroundStyle(onAtmosphere ? .white : Color.farmGreen)
    }
}

// MARK: - Plus lock

/// A locked Plus feature shown in place, not hidden: what it is, and one
/// button to the membership sheet. Frosts nothing — the copy is the teaser.
struct PlusLockCard: View {
    let title: String
    let text: String
    var onUnlock: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Space.s3) {
            Image(systemName: "lock.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.farmGreen)
                .frame(width: 36, height: 36)
                .background(Color.white, in: Circle())
            VStack(alignment: .leading, spacing: Space.s2) {
                HStack(spacing: Space.s2) {
                    Text(title).role(.subheading)
                    Badge(text: "PLUS")
                }
                Text(text).role(.bodySm, .inkMuted)
                Button(String(localized: "Unlock with Farmsy Plus")) { Haptics.tap(); onUnlock() }
                    .buttonStyle(PillButtonStyle(.primary, size: .small))
                    .padding(.top, Space.s1)
            }
        }
        .card(soft: true)
    }
}
